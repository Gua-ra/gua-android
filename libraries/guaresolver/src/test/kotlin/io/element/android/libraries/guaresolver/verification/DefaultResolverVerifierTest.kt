/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.guaresolver.FakeGuaDeployment
import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.ResolvedHomeserver
import io.element.android.libraries.guaresolver.genesis.CachingPreferenceDataStoreFactory
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import java.time.Instant

/**
 * GUA FORK: the orchestrator over a scripted resolver. The artifacts are the published vectors, so what
 * is served here is exactly what the resolver signs; the trust roots are the vectors' test keys.
 */
class DefaultResolverVerifierTest {
    private val vectors: JsonObject by lazy { VectorFixtures.load(VectorFixtures.CLIENT_VECTORS_RESOURCE) }

    @Test
    fun `without trust roots nothing is fetched and every check is unverifiable`() = runTest {
        val resolver = ScriptedResolver()
        val server = resolver.start()
        val verifier = createVerifier(server, trustRoots = null)

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(resolver.requests).isEmpty()
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("rosterSignature"))
        assertThat(report.environment).isEqualTo("other")
        assertThat(report.rosterVersion).isNull()
        assertThat(verifier.lastReport).isEqualTo(report)
        server.shutdown()
    }

    @Test
    fun `malformed trust roots are reported rather than guessed`() = runTest {
        val resolver = ScriptedResolver()
        val server = resolver.start()
        val verifier = createVerifier(server, trustRoots = "authority:a")

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(resolver.requests).isEmpty()
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.TRUST_ROOTS_MALFORMED))
        server.shutdown()
    }

    @Test
    fun `an unavailable roster is unverifiable`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { MockResponse().setResponseCode(503) } }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.ROSTER_UNAVAILABLE))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.ROSTER_UNAVAILABLE))
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("rosterSignature"))
        server.shutdown()
    }

    @Test
    fun `a transport failure is unverifiable and never throws`() = runTest {
        val server = ScriptedResolver().start()
        val verifier = createVerifier(server)
        server.shutdown()

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.ROSTER_UNAVAILABLE))
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("rosterSignature"))
    }

    @Test
    fun `a roster that does not parse is unverifiable`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok("{\"version\": \"seven\"") } }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        server.shutdown()
    }

    @Test
    fun `a mixed roster is refused before any signature check`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(rosterRejectionJson(MIXED_ROSTER_REJECTION)) } }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.rosterForm).isEqualTo(RosterForm.MIXED)
        assertThat(report.rosterVersion).isEqualTo(7)
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.MIXED_ROSTER_FORM))
        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.Unverifiable(VerificationReason.MIXED_ROSTER_FORM))
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("rosterSignature"))
        assertThat(resolver.requests).containsExactly("/roster")
        server.shutdown()
    }

    @Test
    fun `a roster signed by an untrusted key fails and nothing downstream is trusted`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(rosterRejectionJson(UNTRUSTED_KEY_REJECTION)) } }
        val server = resolver.start()
        // The rejected roster is signed by authority-b, which these roots do not list.
        val verifier = createVerifier(server, trustRoots = "authority:authority-a:${spki("rfc8032-test1")}")

        val report = verifier.verify(A_PHONE, answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Invalid(VerificationReason.UNKNOWN_KEY_ID))
        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.Unverifiable(VerificationReason.ROSTER_UNVERIFIED))
        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Unverifiable(VerificationReason.ROSTER_UNVERIFIED))
        assertThat(report.overall).isEqualTo(OverallOutcome.Failed("rosterSignature"))
        assertThat(resolver.requests).containsExactly("/roster")
        server.shutdown()
    }

    @Test
    fun `a verified roster with no policy answers every check`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(rosterJson(R2)) } }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.rosterVersion).isEqualTo(7)
        assertThat(report.rosterForm).isEqualTo(RosterForm.V1)
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Valid)
        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.ActiveMatch)
        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Unattested)
        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.FirstSeen)
        assertThat(report.policy).isEqualTo(PolicyOutcome.NotPresent)
        assertThat(report.placement).isEqualTo(PlacementOutcome.Match)
        assertThat(report.overall).isEqualTo(OverallOutcome.Verified)
        assertThat(resolver.requests).containsExactly("/roster", "/policy/routing").inOrder()
        assertThat(report.toLogLine()).doesNotContain(A_PHONE)
        assertThat(report.toLogLine()).doesNotContain("gua.test")
        server.shutdown()
    }

    @Test
    fun `a known server name at another address is a field mismatch`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(rosterJson(R2)) } }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsBr().copy(baseUrl = "https://matrix.evil.test")))

        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.FieldMismatch)
        assertThat(report.overall).isEqualTo(OverallOutcome.Failed("homeserverMatch"))
        server.shutdown()
    }

    @Test
    fun `the checkpoint pin moves forward only on a verified extension`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(unattestedRosterJson(R1)) } }
        val server = resolver.start()
        val verifier = createVerifier(server)

        assertThat(verifier.verify(A_PHONE, answer(hsBr())).logConsistency).isEqualTo(LogConsistencyOutcome.FirstSeen)

        resolver.roster = { ok(rosterJson(R2)) }
        resolver.consistency = { ok(consistencyJson(first = 3, second = 7)) }
        assertThat(verifier.verify(A_PHONE, answer(hsCa())).logConsistency).isEqualTo(LogConsistencyOutcome.Consistent)
        assertThat(resolver.requests.filter { it.startsWith("/roster/log/consistency") }).containsExactly("/roster/log/consistency?first=3&second=7")

        resolver.roster = { ok(unattestedRosterJson(R1)) }
        val rollback = verifier.verify(A_PHONE, answer(hsBr()))
        assertThat(rollback.logConsistency).isEqualTo(LogConsistencyOutcome.Rollback)
        assertThat(rollback.overall).isEqualTo(OverallOutcome.Failed("logConsistency"))
        server.shutdown()
    }

    @Test
    fun `a checkpoint that does not extend the pin is inconsistent and the pin stays`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(unattestedRosterJson(R1)) } }
        val server = resolver.start()
        val verifier = createVerifier(server)
        verifier.verify(A_PHONE, answer(hsBr()))

        resolver.roster = { ok(rosterJson(R2)) }
        resolver.consistency = { ok(consistencyJson(first = 3, second = 7, tampered = true)) }
        val inconsistent = verifier.verify(A_PHONE, answer(hsCa()))
        assertThat(inconsistent.logConsistency).isEqualTo(LogConsistencyOutcome.Inconsistent)
        assertThat(inconsistent.overall).isEqualTo(OverallOutcome.Failed("logConsistency"))

        resolver.roster = { ok(unattestedRosterJson(R1)) }
        assertThat(verifier.verify(A_PHONE, answer(hsBr())).logConsistency).isEqualTo(LogConsistencyOutcome.Consistent)
        server.shutdown()
    }

    @Test
    fun `an absent consistency endpoint is unverifiable and the pin stays`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(unattestedRosterJson(R1)) } }
        val server = resolver.start()
        val verifier = createVerifier(server)
        verifier.verify(A_PHONE, answer(hsBr()))

        resolver.roster = { ok(rosterJson(R2)) }
        val mirror = verifier.verify(A_PHONE, answer(hsCa()))
        assertThat(mirror.logConsistency).isEqualTo(LogConsistencyOutcome.Unverifiable(VerificationReason.LOG_UNAVAILABLE))
        assertThat(mirror.overall).isEqualTo(OverallOutcome.Unverifiable("logConsistency"))

        resolver.roster = { ok(unattestedRosterJson(R1)) }
        assertThat(verifier.verify(A_PHONE, answer(hsBr())).logConsistency).isEqualTo(LogConsistencyOutcome.Consistent)
        server.shutdown()
    }

    @Test
    fun `the pin is kept across verifier instances for the same resolver`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(unattestedRosterJson(R1)) } }
        val server = resolver.start()
        val dataStoreFactory = CachingPreferenceDataStoreFactory()
        createVerifier(server, dataStoreFactory = dataStoreFactory).verify(A_PHONE, answer(hsBr()))

        val report = createVerifier(server, dataStoreFactory = dataStoreFactory).verify(A_PHONE, answer(hsBr()))

        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Consistent)
        server.shutdown()
    }

    @Test
    fun `a served policy is verified under the policy keys and against the log`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(rosterJson(R2)) }
            policy = { ok(policyJson(0)) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_CANADIAN_PHONE, answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Valid)
        assertThat(report.placement).isEqualTo(PlacementOutcome.Match)
        assertThat(report.overall).isEqualTo(OverallOutcome.Verified)
        assertThat(resolver.requests).containsExactly("/roster", "/policy/routing", "/roster/log").inOrder()
        server.shutdown()
    }

    @Test
    fun `a placement the verified policy routes elsewhere is a mismatch`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(rosterJson(R2)) }
            policy = { ok(policyJson(0)) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Valid)
        assertThat(report.placement).isEqualTo(PlacementOutcome.Mismatch)
        assertThat(report.overall).isEqualTo(OverallOutcome.Failed("placement"))
        server.shutdown()
    }

    @Test
    fun `a policy signed by the operational key is invalid`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(rosterJson(R2)) }
            policy = { ok(policyRejectionJson(OPERATIONAL_KEY_REJECTION)) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Invalid(VerificationReason.UNKNOWN_KEY_ID))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        assertThat(report.overall).isEqualTo(OverallOutcome.Failed("policy"))
        server.shutdown()
    }

    @Test
    fun `a served policy without policy keys is unverifiable, never trusted through the authority keys`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(rosterJson(R2)) }
            policy = { ok(policyJson(0)) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()
        val verifier = createVerifier(server, trustRoots = authorityRoots())

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.NO_POLICY_KEYS))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("policy"))
        server.shutdown()
    }

    @Test
    fun `a policy whose log is not served is unverifiable`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(rosterJson(R2)) }
            policy = { ok(policyJson(0)) }
        }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.LOG_UNAVAILABLE))
        server.shutdown()
    }

    @Test
    fun `a log that does not commit to the roster checkpoint makes the policy invalid`() = runTest {
        val events = JsonArray(vectors["log"]!!.jsonObject["events"]!!.jsonArray.drop(1))
        val resolver = ScriptedResolver().apply {
            roster = { ok(rosterJson(R2)) }
            policy = { ok(policyJson(0)) }
            log = { ok(logJson(events)) }
        }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Invalid(VerificationReason.LOG_MISMATCH))
        server.shutdown()
    }

    @Test
    fun `an existing account answer is indeterminate unless the placement agrees`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(rosterJson(R2)) } }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val differing = verifier.verify(A_PHONE_PLACED_ON_BR, answer(hsCa(), exists = true))
        assertThat(differing.placement).isEqualTo(PlacementOutcome.Indeterminate)
        assertThat(differing.overall).isEqualTo(OverallOutcome.Unverifiable("placement"))

        val agreeing = verifier.verify(A_PHONE_PLACED_ON_BR, answer(hsBr(), exists = true))
        assertThat(agreeing.placement).isEqualTo(PlacementOutcome.Match)
        server.shutdown()
    }

    @Test
    fun `the member self-signature of the returned entry is verified`() = runTest {
        val now = System.currentTimeMillis()
        val attested = attestedRoster(notBeforeMillis = now - DAY_MILLIS, notAfterMillis = now + 30 * DAY_MILLIS)
        val resolver = ScriptedResolver().apply { roster = { ok(attested.toString()) } }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val report = verifier.verify(A_PHONE, answer(hsBr()))

        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Valid)
        assertThat(report.placement).isEqualTo(PlacementOutcome.Match)
        assertThat(report.overall).isEqualTo(OverallOutcome.Verified)

        resolver.roster = { ok(withMemberSequence(attested, 2).toString()) }
        val tampered = verifier.verify(A_PHONE, answer(hsBr()))
        assertThat(tampered.memberEntry).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.BAD_SIGNATURE))
        assertThat(tampered.overall).isEqualTo(OverallOutcome.Failed("memberEntry"))
        server.shutdown()
    }

    private fun createVerifier(
        server: MockWebServer,
        trustRoots: String? = allRoots(),
        dataStoreFactory: PreferenceDataStoreFactory = CachingPreferenceDataStoreFactory(),
    ) = DefaultResolverVerifier(
        retrofitFactory = RetrofitFactory(
            okHttpClient = { OkHttpClient.Builder().build() },
            json = { DefaultJsonProvider() },
        ),
        dispatchers = CoroutineDispatchers(io = Dispatchers.IO, computation = Dispatchers.Default, main = Dispatchers.Default),
        preferenceDataStoreFactory = dataStoreFactory,
        deployment = FakeGuaDeployment(resolverBaseUrl = server.url("/").toString(), resolverTrustRoots = trustRoots),
    )

    private fun authorityRoots(): String = "authority:authority-a:${spki("rfc8032-test1")} authority:authority-b:${spki("rfc8032-test2")}"

    private fun allRoots(): String = "${authorityRoots()} policy:governance-a:${spki("rfc8032-test3")}"

    private fun spki(keyRef: String): String = vectors["keys"]!!.jsonObject[keyRef]!!.jsonObject.string("publicKeySpkiB64")

    private fun seed(keyRef: String): String = vectors["keys"]!!.jsonObject[keyRef]!!.jsonObject.string("seedHex")

    private fun rosterJson(index: Int): String = vectors["rosters"]!!.jsonArray[index].jsonObject["roster"]!!.toString()

    /** The roster without its member blocks, which the authority signature does not cover, so a member window never dates a test. */
    private fun unattestedRosterJson(index: Int): String {
        val roster = vectors["rosters"]!!.jsonArray[index].jsonObject["roster"]!!.jsonObject
        val entries = roster["entries"]!!.jsonArray.map { JsonObject(it.jsonObject - "member") }
        return JsonObject(roster + ("entries" to JsonArray(entries))).toString()
    }

    private fun rosterRejectionJson(index: Int): String = vectors["rosterRejections"]!!.jsonArray[index].jsonObject["roster"]!!.toString()

    private fun policyJson(index: Int): String = vectors["policies"]!!.jsonArray[index].jsonObject["bundle"]!!.toString()

    private fun policyRejectionJson(index: Int): String = vectors["policyRejections"]!!.jsonArray[index].jsonObject["bundle"]!!.toString()

    private fun logJson(events: JsonArray = vectors["log"]!!.jsonObject["events"]!!.jsonArray): String {
        val head = buildJsonObject {
            put("merkleRoot", vectors["log"]!!.jsonObject["roots"]!!.jsonArray.last().jsonObject.string("rootHex"))
            put("size", events.size)
        }
        return buildJsonObject {
            put("head", head)
            put("events", events)
        }.toString()
    }

    private fun consistencyJson(first: Int, second: Int, tampered: Boolean = false): String {
        val vector = vectors["log"]!!.jsonObject["consistencyProofs"]!!.jsonArray.map { it.jsonObject }
            .first { it.int("first") == first && it.int("second") == second }
        val proof = vector["proof"]!!.jsonArray.map { it.jsonPrimitive.content }.toMutableList()
        if (tampered) proof[proof.lastIndex] = proof.last().replaceRange(0, 1, if (proof.last().startsWith("0")) "1" else "0")
        return buildJsonObject {
            put("first", first)
            put("second", second)
            put("proof", JsonArray(proof.map { JsonPrimitive(it) }))
        }.toString()
    }

    /** Roster 1 with its member attestation re-signed by the member's own test key over a window around now. */
    private fun attestedRoster(notBeforeMillis: Long, notAfterMillis: Long): JsonObject {
        val roster = vectors["rosters"]!!.jsonArray[R1].jsonObject["roster"]!!.jsonObject
        val entry = roster["entries"]!!.jsonArray.single().jsonObject
        val homeserver = Json { ignoreUnknownKeys = true }.decodeFromJsonElement<RosterHomeserver>(entry["homeserver"]!!)
        val member = Json.decodeFromJsonElement<MemberAttestation>(entry["member"]!!.jsonObject).copy(
            notBefore = Instant.ofEpochMilli(notBeforeMillis).toString(),
            notAfter = Instant.ofEpochMilli(notAfterMillis).toString(),
        )
        val signature = VectorFixtures.sign(seed("rfc8032-test-sha-abc"), CanonicalMemberEntry.bytes(homeserver, member))
        val signed = member.copy(signatures = listOf(MemberSignature(keyId = member.keyId, signatureB64 = signature)))
        val attestedEntry = JsonObject(entry + ("member" to Json.encodeToJsonElement(signed)))
        return JsonObject(roster + ("entries" to JsonArray(listOf(attestedEntry))))
    }

    private fun withMemberSequence(roster: JsonObject, sequence: Long): JsonObject {
        val entry = roster["entries"]!!.jsonArray.single().jsonObject
        val member = JsonObject(entry["member"]!!.jsonObject + ("sequence" to JsonPrimitive(sequence)))
        return JsonObject(roster + ("entries" to JsonArray(listOf(JsonObject(entry + ("member" to member))))))
    }

    private fun answer(homeserver: ResolvedHomeserver, exists: Boolean = false) = HomeserverResolution(exists = exists, homeserver = homeserver)

    private fun hsBr() = ResolvedHomeserver(
        serverName = "br.gua.test",
        baseUrl = "https://matrix.br.gua.test",
        masIssuer = "https://account.br.gua.test",
        region = "BR",
    )

    private fun hsCa() = ResolvedHomeserver(
        serverName = "ca.gua.test",
        baseUrl = "https://matrix.ca.gua.test",
        masIssuer = "https://account.ca.gua.test",
        region = null,
    )

    private fun ok(body: String) = MockResponse().setBody(body)

    /** Answers each artifact path from a lambda, so a test rewrites what is served between runs. */
    private class ScriptedResolver {
        var roster: () -> MockResponse = { notFound() }
        var log: () -> MockResponse = { notFound() }
        var consistency: () -> MockResponse = { notFound() }
        var policy: () -> MockResponse = { notFound() }
        val requests: MutableList<String> = mutableListOf()

        fun start(): MockWebServer = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    requests += path
                    return when (path.substringBefore('?')) {
                        "/roster" -> roster()
                        "/roster/log" -> log()
                        "/roster/log/consistency" -> consistency()
                        "/policy/routing" -> policy()
                        else -> notFound()
                    }
                }
            }
            start()
        }

        private fun notFound() = MockResponse().setResponseCode(404)
    }

    private companion object {
        private const val A_PHONE = "+5511987654321"
        private const val A_CANADIAN_PHONE = "+14165550123"
        private const val A_PHONE_PLACED_ON_BR = "+5521990001237"
        private const val R1 = 0
        private const val R2 = 1
        private const val UNTRUSTED_KEY_REJECTION = 1
        private const val MIXED_ROSTER_REJECTION = 5
        private const val OPERATIONAL_KEY_REJECTION = 0
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
