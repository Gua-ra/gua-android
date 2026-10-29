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
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** The report-only checks of the resolver verification protocol. An exception settles its own check as internal_error; an Error reaches the caller. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultResolverVerifier(
    private val retrofitFactory: RetrofitFactory,
    private val dispatchers: CoroutineDispatchers,
    private val systemClock: SystemClock,
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
    private val deployment: GuaDeployment = GuaResolverConfig.current,
) : ResolverVerifier {
    private val checkpointPin = ResolverCheckpointPin(preferenceDataStoreFactory.create(STORE_NAME))

    override suspend fun verify(e164Phone: String, resolution: HomeserverResolution): ResolverVerificationReport? {
        val baseUrl = deployment.resolverBaseUrl ?: return null
        val startedAt = System.nanoTime()
        val checks = Checks()
        try {
            withContext(dispatchers.computation) {
                withTimeout(BUDGET_MILLIS) {
                    // Runs against one resolver never overlap, so none can pin from a roster older than another's.
                    runLocks.getOrPut(baseUrl) { Mutex() }.withLock { run(baseUrl, e164Phone, resolution, checks) }
                }
            }
        } catch (e: TimeoutCancellationException) {
            checks.stop(VerificationReason.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            checks.stop(VerificationReason.INTERNAL_ERROR)
        }
        val environment = if (deployment == GuaDeployment.Production) "prod" else "dev"
        val report = checks.toReport(environment, durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI)
        Timber.i(report.toLogLine())
        return report
    }

    private suspend fun run(baseUrl: String, e164Phone: String, resolution: HomeserverResolution, checks: Checks) {
        val roots = trustRoots()
        if (roots == null) {
            checks.skipAll(VerificationReason.NO_TRUST_ROOTS)
            return
        }
        val documents = ResolverDocuments(retrofitFactory.createUnlogged(baseUrl.ensureProtocol()))
        val nowMillis = systemClock.epochMillis()

        val rosterStage = verifyRoster(documents, roots)
        checks.rosterForm = rosterStage.form
        checks.rosterSignature = rosterStage.signature
        val roster = rosterStage.roster
        if (roster == null) {
            checks.stop(VerificationReason.ROSTER_UNVERIFIED)
            return
        }
        checks.rosterVersion = roster.version

        val answer = resolution.homeserver
        val located = RosterVerifier.locate(answer.serverName, answer.baseUrl, answer.masIssuer, roster)
        checks.homeserverMatch = located.match
        checks.memberEntry = orInternalError(MemberEntryOutcome::Unverifiable) { verifyMemberEntry(located, nowMillis) }
        // A directory lookup cannot be reproduced, so an existing account's placement is settled before anything is fetched.
        if (resolution.exists) checks.placement = PlacementOutcome.Indeterminate(VerificationReason.EXISTING_ACCOUNT)

        checks.logConsistency = orInternalError(LogConsistencyOutcome::Unverifiable) { verifyLogConsistency(documents, baseUrl, roster.logCheckpoint) }
        val policy = orInternalError({ PolicyResult(PolicyOutcome.Unverifiable(it)) }) { verifyPolicy(documents, roots, roster.logCheckpoint, nowMillis) }
        checks.policy = policy.outcome
        if (!resolution.exists) {
            checks.placement = orInternalError(PlacementOutcome::Unverifiable) { reproducePlacement(e164Phone, resolution, roster, policy, nowMillis) }
        }
    }

    /** An unexpected exception settles only the check it came from. */
    private inline fun <T> orInternalError(unverifiable: (String) -> T, check: () -> T): T = try {
        check()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        unverifiable(VerificationReason.INTERNAL_ERROR)
    }

    /** No usable authority key: none configured, or a configuration that does not parse. */
    private fun trustRoots(): ResolverTrustRoots? {
        val value = deployment.resolverTrustRoots?.takeIf { it.isNotBlank() } ?: return null
        return tryOrNull { ResolverTrustRoots.parse(value) }?.takeIf { it.authorityKeys.isNotEmpty() }
    }

    private suspend fun verifyRoster(documents: ResolverDocuments, roots: ResolverTrustRoots): RosterStage =
        when (val fetched = documents.roster()) {
            is Fetched.Ok -> {
                val served = fetched.value
                val signature = RosterVerifier(roots.authorityKeys, roots.authorityThreshold).verify(served.roster, served.form)
                RosterStage(form = served.form, signature = signature, roster = served.roster.takeIf { signature == RosterSignatureOutcome.Valid })
            }
            Fetched.Malformed -> RosterStage(form = null, signature = RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR), roster = null)
            Fetched.NotFound, Fetched.Failed -> RosterStage(
                form = null,
                signature = RosterSignatureOutcome.Unverifiable(VerificationReason.ROSTER_UNAVAILABLE),
                roster = null,
            )
        }

    private fun verifyMemberEntry(located: Located, nowMillis: Long): MemberEntryOutcome {
        val entry = located.entry
        val attestable = located.match == HomeserverMatchOutcome.ActiveMatch || located.match == HomeserverMatchOutcome.Inactive
        if (entry == null || !attestable) return MemberEntryOutcome.Unverifiable(VerificationReason.NO_MATCHING_ENTRY)
        return MemberEntryVerifier.verify(entry.homeserver, entry.member, nowMillis)
    }

    private suspend fun verifyLogConsistency(documents: ResolverDocuments, baseUrl: String, served: LogCheckpoint): LogConsistencyOutcome = try {
        compareWithPin(documents, baseUrl, served)
    } catch (e: IOException) {
        LogConsistencyOutcome.Unverifiable(VerificationReason.STORAGE_UNAVAILABLE)
    }

    private suspend fun compareWithPin(documents: ResolverDocuments, baseUrl: String, served: LogCheckpoint): LogConsistencyOutcome {
        // A pin of size zero is no pin: everything extends the empty tree, so nothing would be checked.
        val pinned = checkpointPin.read(baseUrl)?.takeIf { it.size > 0L }
        if (pinned == null) {
            checkpointPin.advance(baseUrl, served)
            return LogConsistencyOutcome.FirstSeen
        }
        if (served.size < pinned.size) return LogConsistencyOutcome.Rollback
        if (served.size == pinned.size) {
            return if (served.merkleRoot == pinned.merkleRoot) LogConsistencyOutcome.Consistent else LogConsistencyOutcome.Inconsistent
        }
        val proof = when (val fetched = documents.consistency(first = pinned.size, second = served.size)) {
            is Fetched.Ok -> fetched.value.proof
            Fetched.Malformed -> return LogConsistencyOutcome.Unverifiable(VerificationReason.PARSE_ERROR)
            Fetched.NotFound, Fetched.Failed -> return LogConsistencyOutcome.Unverifiable(VerificationReason.LOG_UNAVAILABLE)
        }
        if (!MerkleTree.verifyConsistency(pinned.size, served.size, pinned.merkleRoot, served.merkleRoot, proof)) {
            return LogConsistencyOutcome.Inconsistent
        }
        checkpointPin.advance(baseUrl, served)
        return LogConsistencyOutcome.Consistent
    }

    private suspend fun verifyPolicy(documents: ResolverDocuments, roots: ResolverTrustRoots, checkpoint: LogCheckpoint, nowMillis: Long): PolicyResult {
        val bundle = when (val fetched = documents.routingPolicy()) {
            is Fetched.Ok -> fetched.value
            Fetched.NotFound -> return PolicyResult(verifyPolicyLog(documents, checkpoint, served = null))
            Fetched.Malformed -> return PolicyResult(PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
            Fetched.Failed -> return PolicyResult(PolicyOutcome.Unverifiable(VerificationReason.POLICY_UNAVAILABLE))
        }
        val canonical = tryOrNull { CanonicalRoutingPolicy.bytes(bundle) }
            ?: return PolicyResult(PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        val verifier = RoutingPolicyVerifier(roots.policyKeys, roots.policyThreshold)
        val signatures = verifier.verify(bundle, canonical)
        if (signatures != PolicyOutcome.Valid) return PolicyResult(signatures)
        if (!bundle.isInEffectAt(nowMillis)) return PolicyResult(PolicyOutcome.Invalid(VerificationReason.OUTSIDE_WINDOW))
        val logged = verifyPolicyLog(documents, checkpoint, LoggedPolicy(bundle.policyId, bundle.version, MerkleTree.sha256Hex(canonical)))
        if (logged != PolicyOutcome.Valid) return PolicyResult(logged)
        return PolicyResult(PolicyOutcome.Valid, VerifiedPolicy(bundle, verifier.delegateVerifiedZones(bundle)))
    }

    private suspend fun verifyPolicyLog(documents: ResolverDocuments, checkpoint: LogCheckpoint, served: LoggedPolicy?): PolicyOutcome {
        if (checkpoint.size > MAX_LOG_SIZE) return PolicyOutcome.Unverifiable(VerificationReason.LOG_TOO_LARGE)
        return when (val fetched = documents.log()) {
            is Fetched.Ok -> PolicyLog.check(served, checkpoint, fetched.value.events)
            Fetched.Malformed -> PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR)
            Fetched.NotFound, Fetched.Failed -> PolicyOutcome.Unverifiable(VerificationReason.LOG_UNAVAILABLE)
        }
    }

    /** A placement that differs counts against the resolver only when the answer names the roster version verified here. */
    private fun reproducePlacement(
        e164Phone: String,
        resolution: HomeserverResolution,
        roster: SignedRoster,
        policy: PolicyResult,
        nowMillis: Long,
    ): PlacementOutcome {
        if (policy.outcome.verdict != Verdict.PASS) return PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED)
        val context = PlacementContext(e164Phone = e164Phone)
        val verified = policy.verified
        val decision = PlacementEngine.decide(context, roster, verified?.bundle, verified?.delegateVerifiedZones.orEmpty(), nowMillis)
        if (verified != null && PlacementEngine.unsignedRuleOutranks(context, verified.bundle, verified.delegateVerifiedZones, decision, nowMillis)) {
            return PlacementOutcome.Indeterminate(VerificationReason.UNSIGNED_RULE)
        }
        val decided = decision?.let { roster.entries.firstOrNull { entry -> entry.homeserver.id == it.homeserverId } }
        return when {
            decided != null && decided.homeserver.sameEndpointAs(resolution.homeserver) -> PlacementOutcome.Match
            resolution.rosterVersion == roster.version -> PlacementOutcome.Mismatch
            else -> PlacementOutcome.Indeterminate(VerificationReason.UNATTRIBUTED)
        }
    }

    private fun RosterHomeserver.sameEndpointAs(answer: ResolvedHomeserver): Boolean =
        serverName == answer.serverName && baseUrl == answer.baseUrl && masIssuer == answer.masIssuer

    private data class RosterStage(val form: RosterForm?, val signature: RosterSignatureOutcome, val roster: SignedRoster?)

    private data class PolicyResult(val outcome: PolicyOutcome, val verified: VerifiedPolicy? = null)

    private data class VerifiedPolicy(val bundle: RoutingPolicyBundle, val delegateVerifiedZones: Set<String>)

    /** The report under construction: a check is null until it settles. */
    private class Checks {
        var rosterForm: RosterForm? = null
        var rosterVersion: Long? = null
        var rosterSignature: RosterSignatureOutcome? = null
        var homeserverMatch: HomeserverMatchOutcome? = null
        var memberEntry: MemberEntryOutcome? = null
        var logConsistency: LogConsistencyOutcome? = null
        var policy: PolicyOutcome? = null
        var placement: PlacementOutcome? = null

        fun skipAll(reason: String) {
            rosterSignature = RosterSignatureOutcome.Unverifiable(reason)
            homeserverMatch = HomeserverMatchOutcome.Unverifiable(reason)
            memberEntry = MemberEntryOutcome.Unverifiable(reason)
            logConsistency = LogConsistencyOutcome.Unverifiable(reason)
            policy = PolicyOutcome.Unverifiable(reason)
            placement = PlacementOutcome.Unverifiable(reason)
        }

        /**
         * Settles the checks the run did not reach with [reason], or with roster_unverified when the roster did
         * not verify. A placement still waiting on a policy that did not pass is policy_unverified.
         */
        fun stop(reason: String) {
            val rosterVerified = rosterSignature == RosterSignatureOutcome.Valid
            if (rosterSignature == null) rosterSignature = RosterSignatureOutcome.Unverifiable(reason)
            val unreached = if (rosterVerified) reason else VerificationReason.ROSTER_UNVERIFIED
            homeserverMatch = homeserverMatch ?: HomeserverMatchOutcome.Unverifiable(unreached)
            memberEntry = memberEntry ?: MemberEntryOutcome.Unverifiable(unreached)
            logConsistency = logConsistency ?: LogConsistencyOutcome.Unverifiable(unreached)
            val policyPassed = policy?.verdict == Verdict.PASS
            policy = policy ?: PolicyOutcome.Unverifiable(unreached)
            placement = placement ?: PlacementOutcome.Unverifiable(if (rosterVerified && !policyPassed) VerificationReason.POLICY_UNVERIFIED else unreached)
        }

        fun toReport(environment: String, durationMs: Long) = ResolverVerificationReport(
            environment = environment,
            rosterVersion = rosterVersion,
            rosterForm = rosterForm,
            rosterSignature = checkNotNull(rosterSignature),
            homeserverMatch = checkNotNull(homeserverMatch),
            memberEntry = checkNotNull(memberEntry),
            logConsistency = checkNotNull(logConsistency),
            policy = checkNotNull(policy),
            placement = checkNotNull(placement),
            durationMs = durationMs,
        )
    }

    private companion object {
        private const val STORE_NAME = "gua_resolver_verification"
        private const val BUDGET_MILLIS = 10_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MAX_LOG_SIZE = 4096L

        /** One lock per resolver base URL, shared by every verifier in the process. */
        private val runLocks = ConcurrentHashMap<String, Mutex>()
    }
}
