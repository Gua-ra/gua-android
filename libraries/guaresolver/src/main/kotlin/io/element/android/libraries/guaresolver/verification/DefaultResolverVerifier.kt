/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.ResolvedHomeserver
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * Default [ResolverVerifier]: the checks of the resolver verification protocol, report-only. It runs
 * detached from the sign-in path under a fixed time budget, logs outcome codes only, and keeps the last
 * report in memory. Without trust roots for the deployment nothing is fetched.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultResolverVerifier(
    private val retrofitFactory: RetrofitFactory,
    private val dispatchers: CoroutineDispatchers,
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
    private val deployment: GuaDeployment = GuaResolverConfig.current,
) : ResolverVerifier {
    private val checkpointPin = ResolverCheckpointPin(preferenceDataStoreFactory.create(STORE_NAME))
    private val memberEntryVerifier = MemberEntryVerifier()

    @Volatile
    var lastReport: ResolverVerificationReport? = null
        private set

    override suspend fun verify(e164Phone: String, resolution: HomeserverResolution): ResolverVerificationReport {
        val startedAt = System.nanoTime()
        val checks = Checks(environment = environmentLabel())
        try {
            withContext(dispatchers.computation) {
                withTimeout(BUDGET_MILLIS) { run(e164Phone, resolution.homeserver, resolution.exists, checks) }
            }
        } catch (e: TimeoutCancellationException) {
            checks.abort(VerificationReason.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            checks.abort(VerificationReason.INTERNAL_ERROR)
        }
        val report = checks.toReport(durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI)
        Timber.i(report.toLogLine())
        lastReport = report
        return report
    }

    private suspend fun run(e164Phone: String, answer: ResolvedHomeserver, exists: Boolean, checks: Checks) {
        val baseUrl = deployment.resolverBaseUrl ?: return checks.abort(VerificationReason.RESOLVER_NOT_CONFIGURED)
        val rootsValue = deployment.resolverTrustRoots?.takeIf { it.isNotBlank() } ?: return checks.abort(VerificationReason.NO_TRUST_ROOTS)
        val roots = tryOrNull { ResolverTrustRoots.parse(rootsValue) } ?: return checks.abort(VerificationReason.TRUST_ROOTS_MALFORMED)
        if (roots.authorityKeys.isEmpty()) return checks.abort(VerificationReason.NO_TRUST_ROOTS)
        val api = retrofitFactory.create(baseUrl.ensureProtocol()).create(ResolverVerificationApi::class.java)
        val now = System.currentTimeMillis()

        val roster = when (val fetched = fetch { api.roster() }) {
            is Fetched.Ok -> fetched.value
            Fetched.Malformed -> return checks.abort(VerificationReason.PARSE_ERROR)
            Fetched.NotFound, Fetched.Failed -> return checks.abort(VerificationReason.ROSTER_UNAVAILABLE)
        }
        checks.rosterVersion = roster.version
        val form = CanonicalRoster.formOf(roster)
        checks.rosterForm = form
        val signature = RosterVerifier(roots.authorityKeys, roots.authorityThreshold).verify(roster, form)
        checks.rosterSignature = signature
        if (signature != RosterSignatureOutcome.Valid) {
            return checks.abort(if (form == RosterForm.MIXED) VerificationReason.MIXED_ROSTER_FORM else VerificationReason.ROSTER_UNVERIFIED)
        }

        checks.homeserverMatch = RosterVerifier.locate(answer.serverName, answer.baseUrl, answer.masIssuer, roster)
        val entry = roster.entries.firstOrNull { it.homeserver.sameEndpointAs(answer) }
            ?: roster.entries.firstOrNull { it.homeserver.serverName == answer.serverName }
        checks.memberEntry = entry?.let { memberEntryVerifier.verify(it.homeserver, it.member, now) }
            ?: MemberEntryOutcome.Unverifiable(VerificationReason.NOT_IN_ROSTER)
        checks.logConsistency = verifyLogConsistency(api, baseUrl, roster.logCheckpoint)
        val policy = verifyPolicy(api, roots, roster.logCheckpoint, checks)
        checks.placement = reproducePlacement(e164Phone, answer, exists, roster, policy, checks.policy, now)
    }

    private suspend fun verifyLogConsistency(api: ResolverVerificationApi, baseUrl: String, served: LogCheckpoint): LogConsistencyOutcome {
        // A pin of size zero is no pin: everything extends the empty tree, so nothing would be checked.
        val pinned = checkpointPin.read(baseUrl)?.takeIf { it.size > 0L }
        return when {
            pinned == null -> {
                checkpointPin.write(baseUrl, served)
                LogConsistencyOutcome.FirstSeen
            }
            served.size == pinned.size -> {
                if (served.merkleRoot == pinned.merkleRoot) LogConsistencyOutcome.Consistent else LogConsistencyOutcome.Inconsistent
            }
            served.size < pinned.size -> LogConsistencyOutcome.Rollback
            else -> when (val fetched = fetch { api.consistency(first = pinned.size, second = served.size) }) {
                is Fetched.Ok -> {
                    val extends = MerkleTree.verifyConsistency(pinned.size, served.size, pinned.merkleRoot, served.merkleRoot, fetched.value.proof)
                    if (extends) {
                        checkpointPin.write(baseUrl, served)
                        LogConsistencyOutcome.Consistent
                    } else {
                        LogConsistencyOutcome.Inconsistent
                    }
                }
                Fetched.Malformed -> LogConsistencyOutcome.Unverifiable(VerificationReason.PARSE_ERROR)
                Fetched.NotFound, Fetched.Failed -> LogConsistencyOutcome.Unverifiable(VerificationReason.LOG_UNAVAILABLE)
            }
        }
    }

    /** Sets the policy check and returns the bundle with its delegate-verified zones only when that check is valid. */
    private suspend fun verifyPolicy(
        api: ResolverVerificationApi,
        roots: ResolverTrustRoots,
        checkpoint: LogCheckpoint,
        checks: Checks,
    ): VerifiedPolicy? {
        val bundle = when (val fetched = fetch { api.routingPolicy() }) {
            is Fetched.Ok -> fetched.value
            Fetched.NotFound -> return null.also { checks.policy = PolicyOutcome.NotPresent }
            Fetched.Malformed -> return null.also { checks.policy = PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR) }
            Fetched.Failed -> return null.also { checks.policy = PolicyOutcome.Unverifiable(VerificationReason.POLICY_UNAVAILABLE) }
        }
        if (roots.policyKeys.isEmpty()) return null.also { checks.policy = PolicyOutcome.Unverifiable(VerificationReason.NO_POLICY_KEYS) }
        val canonical = tryOrNull { CanonicalRoutingPolicy.bytes(bundle) }
            ?: return null.also { checks.policy = PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR) }
        val verifier = RoutingPolicyVerifier(roots.policyKeys, roots.policyThreshold)
        val check = verifier.check(bundle, canonical)
        if (!check.met) return null.also { checks.policy = PolicyOutcome.Invalid(check.failureReason) }
        val outcome = verifyPolicyLogged(api, checkpoint, MerkleTree.sha256Hex(canonical))
        checks.policy = outcome
        return if (outcome == PolicyOutcome.Valid) VerifiedPolicy(bundle, verifier.delegateVerifiedZones(bundle)) else null
    }

    /** The served policy must be the publicly logged one: a POLICY_PUBLISH leaf in the log the roster's checkpoint commits to. */
    private suspend fun verifyPolicyLogged(api: ResolverVerificationApi, checkpoint: LogCheckpoint, policyHash: String): PolicyOutcome {
        val log = when (val fetched = fetch { api.log() }) {
            is Fetched.Ok -> fetched.value
            Fetched.Malformed -> return PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR)
            Fetched.NotFound, Fetched.Failed -> return PolicyOutcome.Unverifiable(VerificationReason.LOG_UNAVAILABLE)
        }
        val leaves = tryOrNull { log.events.map { MerkleTree.leafHash(it.leafData().toByteArray(Charsets.UTF_8)) } }
            ?: return PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR)
        // The log may have grown since the roster was issued; the checkpoint commits to its prefix.
        if (leaves.size < checkpoint.size) return PolicyOutcome.Invalid(VerificationReason.LOG_MISMATCH)
        if (MerkleTree.root(leaves.take(checkpoint.size.toInt())) != checkpoint.merkleRoot) return PolicyOutcome.Invalid(VerificationReason.LOG_MISMATCH)
        val logged = log.events.take(checkpoint.size.toInt()).any { it.type == POLICY_PUBLISH && it.payloadHash == policyHash }
        return if (logged) PolicyOutcome.Valid else PolicyOutcome.Invalid(VerificationReason.NOT_LOGGED)
    }

    private fun reproducePlacement(
        e164Phone: String,
        answer: ResolvedHomeserver,
        exists: Boolean,
        roster: SignedRoster,
        policy: VerifiedPolicy?,
        policyOutcome: PolicyOutcome?,
        nowMillis: Long,
    ): PlacementOutcome {
        if (policyOutcome != PolicyOutcome.NotPresent && policy == null) return PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED)
        val context = PlacementContext(e164Phone = e164Phone)
        val decision = PlacementEngine.decide(context, roster, policy?.bundle, policy?.delegateVerifiedZones.orEmpty(), nowMillis)
        val decided = decision?.let { roster.entries.firstOrNull { entry -> entry.homeserver.id == it.homeserverId }?.homeserver }
        return when {
            decided != null && decided.sameEndpointAs(answer) -> PlacementOutcome.Match
            // A directory hit is not reproducible, so a differing placement says nothing about it.
            exists -> PlacementOutcome.Indeterminate
            else -> PlacementOutcome.Mismatch
        }
    }

    private fun RosterHomeserver.sameEndpointAs(answer: ResolvedHomeserver): Boolean =
        serverName == answer.serverName && baseUrl == answer.baseUrl && masIssuer == answer.masIssuer

    private fun environmentLabel(): String = when (deployment) {
        GuaDeployment.Production -> "prod"
        GuaDeployment.Development -> "dev"
        else -> "other"
    }

    private suspend inline fun <T> fetch(call: () -> T): Fetched<T> = try {
        Fetched.Ok(call())
    } catch (e: HttpException) {
        if (e.code() == HTTP_NOT_FOUND) Fetched.NotFound else Fetched.Failed
    } catch (e: SerializationException) {
        Fetched.Malformed
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Fetched.Failed
    }

    private sealed interface Fetched<out T> {
        data class Ok<T>(val value: T) : Fetched<T>
        data object NotFound : Fetched<Nothing>
        data object Malformed : Fetched<Nothing>
        data object Failed : Fetched<Nothing>
    }

    private data class VerifiedPolicy(val bundle: RoutingPolicyBundle, val delegateVerifiedZones: Set<String>)

    /** The report under construction; a check never reached is reported as unverifiable with the reason the run stopped. */
    private class Checks(val environment: String) {
        var rosterVersion: Long? = null
        var rosterForm: RosterForm? = null
        var rosterSignature: RosterSignatureOutcome? = null
        var homeserverMatch: HomeserverMatchOutcome? = null
        var memberEntry: MemberEntryOutcome? = null
        var logConsistency: LogConsistencyOutcome? = null
        var policy: PolicyOutcome? = null
        var placement: PlacementOutcome? = null
        private var stopReason: String? = null

        fun abort(reason: String) {
            if (stopReason == null) stopReason = reason
        }

        fun toReport(durationMs: Long): ResolverVerificationReport {
            val pending = stopReason ?: VerificationReason.INTERNAL_ERROR
            return ResolverVerificationReport(
                environment = environment,
                rosterVersion = rosterVersion,
                rosterForm = rosterForm,
                rosterSignature = rosterSignature ?: RosterSignatureOutcome.Unverifiable(pending),
                homeserverMatch = homeserverMatch ?: HomeserverMatchOutcome.Unverifiable(pending),
                memberEntry = memberEntry ?: MemberEntryOutcome.Unverifiable(pending),
                logConsistency = logConsistency ?: LogConsistencyOutcome.Unverifiable(pending),
                policy = policy ?: PolicyOutcome.Unverifiable(pending),
                placement = placement ?: PlacementOutcome.Unverifiable(pending),
                durationMs = durationMs,
            )
        }
    }

    private companion object {
        private const val STORE_NAME = "gua_resolver_verification"
        private const val BUDGET_MILLIS = 10_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val HTTP_NOT_FOUND = 404
        private const val POLICY_PUBLISH = "POLICY_PUBLISH"
    }
}
