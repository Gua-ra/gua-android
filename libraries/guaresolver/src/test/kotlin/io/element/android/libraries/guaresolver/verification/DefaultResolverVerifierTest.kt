/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.guaresolver.genesis.CachingPreferenceDataStoreFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The verifier against a scripted resolver serving the published vectors, or vectors signed again with their test keys. */
class DefaultResolverVerifierTest {
    @Test
    fun `without a resolver base URL nothing runs and nothing is reported`() = runTest {
        val resolver = ScriptedResolver()
        val server = resolver.start()
        val verifier = createVerifier(server, resolverBaseUrl = null)

        assertThat(verifier.verify(A_PHONE, answer(hsCa()))).isNull()
        assertThat(verifier.lastReport).isNull()
        assertThat(resolver.requests).isEmpty()
        server.shutdown()
    }

    @Test
    fun `without trust roots nothing is fetched and every check is no_trust_roots`() = runTest {
        val resolver = ScriptedResolver()
        val server = resolver.start()
        val verifier = createVerifier(server, trustRoots = null)

        val report = verify(verifier, answer(hsCa()))

        assertThat(resolver.requests).isEmpty()
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("rosterSignature"))
        assertThat(report.rosterVersion).isNull()
        assertThat(report.rosterForm).isNull()
        assertThat(verifier.lastReport).isEqualTo(report)
        server.shutdown()
    }

    @Test
    fun `trust roots that do not parse are no_trust_roots`() = runTest {
        val resolver = ScriptedResolver()
        val server = resolver.start()

        val report = verify(createVerifier(server, trustRoots = "authority:a"), answer(hsCa()))

        assertThat(resolver.requests).isEmpty()
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.NO_TRUST_ROOTS))
        server.shutdown()
    }

    @Test
    fun `an unavailable roster leaves the other checks roster_unverified`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { MockResponse().setResponseCode(503) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.ROSTER_UNAVAILABLE))
        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.Unverifiable(VerificationReason.ROSTER_UNVERIFIED))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.ROSTER_UNVERIFIED))
        assertThat(report.rosterForm).isNull()
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("rosterSignature"))
        assertThat(resolver.requests).containsExactly("/roster")
        server.shutdown()
    }

    @Test
    fun `a transport failure is roster_unavailable`() = runTest {
        val server = ScriptedResolver().start()
        val verifier = createVerifier(server)
        server.shutdown()

        val report = verify(verifier, answer(hsCa()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.ROSTER_UNAVAILABLE))
    }

    @Test
    fun `a roster that does not parse is a parse error`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok("{\"version\": \"seven\"") } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        assertThat(report.rosterForm).isNull()
        server.shutdown()
    }

    @Test
    fun `a mixed roster is refused before any signature check`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(rosterRejection(MIXED_ROSTER_REJECTION).toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.rosterForm).isEqualTo(RosterForm.MIXED)
        assertThat(report.rosterVersion).isNull()
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.MIXED_ROSTER_FORM))
        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.Unverifiable(VerificationReason.ROSTER_UNVERIFIED))
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("rosterSignature"))
        assertThat(resolver.requests).containsExactly("/roster")
        server.shutdown()
    }

    @Test
    fun `a roster signed by an untrusted key fails and nothing downstream is trusted`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(rosterRejection(UNTRUSTED_KEY_REJECTION).toString()) } }
        val server = resolver.start()
        // The rejected roster is signed by authority-b, which these roots do not list.
        val verifier = createVerifier(server, trustRoots = "authority:authority-a:${spki("rfc8032-test1")}")

        val report = verify(verifier, answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Invalid(VerificationReason.UNKNOWN_KEY_ID))
        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.Unverifiable(VerificationReason.ROSTER_UNVERIFIED))
        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Unverifiable(VerificationReason.ROSTER_UNVERIFIED))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.ROSTER_UNVERIFIED))
        assertThat(report.overall).isEqualTo(OverallOutcome.Failed("rosterSignature"))
        assertThat(resolver.requests).containsExactly("/roster")
        server.shutdown()
    }

    @Test
    fun `a roster whose signature fails reports no roster version`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(rosterRejection(TAMPERED_BASE_URL_REJECTION).toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Invalid(VerificationReason.BAD_SIGNATURE))
        assertThat(report.rosterVersion).isNull()
        assertThat(report.rosterForm).isEqualTo(RosterForm.V1)
        server.shutdown()
    }

    @Test
    fun `a weight past int32 is a parse error`() = runTest {
        val entry = servedRoster("r1").rosterEntries().single()
        val heavy = JsonObject(entry + ("homeserver" to JsonObject(entry["homeserver"]!!.jsonObject + ("weight" to JsonPrimitive(1L shl 31)))))
        val resolver = ScriptedResolver().apply { roster = { ok(JsonObject(servedRoster("r1") + ("entries" to JsonArray(listOf(heavy)))).toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        server.shutdown()
    }

    @Test
    fun `a roster whose every entry has a null searchVisibility is the v1 form`() = runTest {
        val base = servedRoster("r1")
        val entries = base.rosterEntries().map { entry ->
            JsonObject(entry - "member" + ("homeserver" to JsonObject(entry["homeserver"]!!.jsonObject + ("searchVisibility" to JsonNull))))
        }
        val signed = resigned(JsonObject(base + ("entries" to JsonArray(entries))), RosterForm.V1)
        val resolver = ScriptedResolver().apply { roster = { ok(signed.toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterForm).isEqualTo(RosterForm.V1)
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Valid)
        server.shutdown()
    }

    @Test
    fun `deep nesting in an ignored roster field is a parse error`() = runTest {
        val body = JsonObject(servedRoster("r1") + ("padding" to JsonPrimitive(NESTED))).toString().replace("\"$NESTED\"", deepArrays())
        val resolver = ScriptedResolver().apply { roster = { ok(body) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        assertThat(report.durationMs).isLessThan(PROMPT_MILLIS)
        server.shutdown()
    }

    @Test
    fun `deep nesting inside a member block is a parse error and crashes nothing`() = runTest {
        val body = withMember(servedRoster("r1"), JsonPrimitive(NESTED)).toString().replace("\"$NESTED\"", "{\"x\":${deepArrays()}}")
        val resolver = ScriptedResolver().apply { roster = { ok(body) } }
        val server = resolver.start()
        val verifier = createVerifier(server)

        val (report, uncaught) = recordingUncaughtExceptions { verify(verifier, answer(hsBr())) }

        assertThat(uncaught).isEmpty()
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        assertThat(report.durationMs).isLessThan(PROMPT_MILLIS)
        server.shutdown()
    }

    @Test
    fun `a roster body over 2 MiB is a parse error and is not decoded`() = runTest {
        // Valid JSON that verifies once decoded, so only the size cap can refuse it.
        val body = servedRoster("r2").toString().dropLast(1) + " ".repeat(3 * 1024 * 1024) + "}"
        val resolver = ScriptedResolver().apply { roster = { ok(body) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        assertThat(report.rosterForm).isNull()
        server.shutdown()
    }

    @Test
    fun `brackets and escaped quotes inside strings are not nesting`() = runTest {
        val note = "\"" + "[{".repeat(100) + "\\\"" + "]}".repeat(100) + "\""
        val body = JsonObject(servedRoster("r2") + ("note" to JsonPrimitive(NESTED))).toString().replace("\"$NESTED\"", note)
        val resolver = ScriptedResolver().apply { roster = { ok(body) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Valid)
        server.shutdown()
    }

    @Test
    fun `a verified roster with no policy answers every check`() = runTest {
        val resolver = withoutPolicy()
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterVersion).isEqualTo(3)
        assertThat(report.rosterForm).isEqualTo(RosterForm.V1)
        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Valid)
        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.ActiveMatch)
        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Valid)
        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.FirstSeen)
        assertThat(report.policy).isEqualTo(PolicyOutcome.NotPresent)
        assertThat(report.placement).isEqualTo(PlacementOutcome.Match)
        assertThat(report.overall).isEqualTo(OverallOutcome.Verified)
        assertThat(resolver.requests).containsExactly("/roster", "/policy/routing", "/roster/log").inOrder()
        server.shutdown()
    }

    @Test
    fun `the report line carries outcome codes only, in the contract's format`() = runTest {
        val server = withoutPolicy().start()

        val report = verify(createVerifier(server), answer(hsBr(), exists = true))

        assertThat(report.toLogLine()).isEqualTo(
            "Resolver verification environment=dev rosterVersion=3 rosterForm=v1 rosterSignature=valid homeserverMatch=ACTIVE_MATCH " +
                "memberEntry=valid logConsistency=firstSeen policy=notPresent placement=indeterminate(existing_account) " +
                "overall=unverifiable(placement) durationMs=${report.durationMs}"
        )
        server.shutdown()
    }

    @Test
    fun `a known server name at another address is a field mismatch with no matching entry`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(servedRoster("r2").toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr().copy(baseUrl = "https://matrix.evil.test")))

        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.FieldMismatch)
        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Unverifiable(VerificationReason.NO_MATCHING_ENTRY))
        assertThat(report.overall).isEqualTo(OverallOutcome.Failed("homeserverMatch"))
        server.shutdown()
    }

    @Test
    fun `the member block of the returned entry is verified`() = runTest {
        val server = withoutPolicy().start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Valid)
        assertThat(report.overall).isEqualTo(OverallOutcome.Verified)
        server.shutdown()
    }

    @Test
    fun `the member block checked is the one of the entry homeserverMatch located`() = runTest {
        val base = servedRoster("r1")
        val active = base.rosterEntries().single()
        val retiredHomeserver = JsonObject(active["homeserver"]!!.jsonObject + ("id" to JsonPrimitive("hs-br-retired")))
        // Served first, SUSPENDED, with the same three fields and a valid block of its own.
        val retired = JsonObject(
            active + mapOf("homeserver" to retiredHomeserver, "status" to JsonPrimitive("SUSPENDED"), "member" to memberSignedFor(retiredHomeserver, active))
        )
        val broken = JsonObject(active + ("member" to JsonObject(active["member"]!!.jsonObject + ("sequence" to JsonPrimitive(2)))))
        val signed = resigned(JsonObject(base + ("entries" to JsonArray(listOf(retired, broken)))))
        val resolver = ScriptedResolver().apply { roster = { ok(signed.toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.homeserverMatch).isEqualTo(HomeserverMatchOutcome.ActiveMatch)
        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.BAD_SIGNATURE))
        server.shutdown()
    }

    @Test
    fun `a member block without a key id is bad_key_id`() = runTest {
        val member = servedRoster("r1").rosterEntries().single()["member"]!!.jsonObject
        val resolver = ScriptedResolver().apply { roster = { ok(withMember(servedRoster("r1"), JsonObject(member - "keyId")).toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Valid)
        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.BAD_KEY_ID))
        server.shutdown()
    }

    @Test
    fun `a member signature without its value is malformed_signature`() = runTest {
        val member = servedRoster("r1").rosterEntries().single()["member"]!!.jsonObject
        val unsigned = JsonObject(member + ("signatures" to JsonArray(listOf(buildJsonObject { put("keyId", "hs-br-1") }))))
        val resolver = ScriptedResolver().apply { roster = { ok(withMember(servedRoster("r1"), unsigned).toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.MALFORMED_SIGNATURE))
        server.shutdown()
    }

    @Test
    fun `a member block that is not an object fails memberEntry only`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(withMember(servedRoster("r1"), JsonArray(emptyList())).toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Valid)
        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.PARSE_ERROR))
        server.shutdown()
    }

    @Test
    fun `the checkpoint pin moves forward only on a verified extension`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r1").toString()) }
            consistency = { first, second -> ok(consistencyJson(first, second)) }
        }
        val server = resolver.start()
        val verifier = createVerifier(server)

        assertThat(verify(verifier, answer(hsBr())).logConsistency).isEqualTo(LogConsistencyOutcome.FirstSeen)

        resolver.roster = { ok(servedRoster("r2").toString()) }
        assertThat(verify(verifier, answer(hsCa())).logConsistency).isEqualTo(LogConsistencyOutcome.Consistent)
        assertThat(resolver.requests.filter { it.startsWith("/roster/log/consistency") }).containsExactly("/roster/log/consistency?first=3&second=7")

        resolver.roster = { ok(servedRoster("r1").toString()) }
        val rollback = verify(verifier, answer(hsBr()))
        assertThat(rollback.logConsistency).isEqualTo(LogConsistencyOutcome.Rollback)
        assertThat(rollback.overall).isEqualTo(OverallOutcome.Failed("logConsistency"))
        server.shutdown()
    }

    @Test
    fun `a checkpoint that does not extend the pin is inconsistent and the pin stays`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(servedRoster("r1").toString()) } }
        val server = resolver.start()
        val verifier = createVerifier(server)
        verify(verifier, answer(hsBr()))

        resolver.roster = { ok(servedRoster("r2").toString()) }
        resolver.consistency = { first, second -> ok(consistencyJson(first, second, tampered = true)) }
        val inconsistent = verify(verifier, answer(hsCa()))
        assertThat(inconsistent.logConsistency).isEqualTo(LogConsistencyOutcome.Inconsistent)
        assertThat(inconsistent.overall).isEqualTo(OverallOutcome.Failed("logConsistency"))

        resolver.roster = { ok(servedRoster("r1").toString()) }
        assertThat(verify(verifier, answer(hsBr())).logConsistency).isEqualTo(LogConsistencyOutcome.Consistent)
        server.shutdown()
    }

    @Test
    fun `an absent consistency endpoint is unverifiable and the pin stays`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(servedRoster("r1").toString()) } }
        val server = resolver.start()
        val verifier = createVerifier(server)
        verify(verifier, answer(hsBr()))

        resolver.roster = { ok(servedRoster("r2").toString()) }
        val mirror = verify(verifier, answer(hsCa()))
        assertThat(mirror.logConsistency).isEqualTo(LogConsistencyOutcome.Unverifiable(VerificationReason.LOG_UNAVAILABLE))
        assertThat(mirror.overall).isEqualTo(OverallOutcome.Unverifiable("logConsistency"))

        resolver.roster = { ok(servedRoster("r1").toString()) }
        assertThat(verify(verifier, answer(hsBr())).logConsistency).isEqualTo(LogConsistencyOutcome.Consistent)
        server.shutdown()
    }

    @Test
    fun `the pin is kept across verifier instances for the same resolver`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(servedRoster("r1").toString()) } }
        val server = resolver.start()
        val dataStoreFactory = CachingPreferenceDataStoreFactory()
        verify(createVerifier(server, dataStoreFactory = dataStoreFactory), answer(hsBr()))

        val report = verify(createVerifier(server, dataStoreFactory = dataStoreFactory), answer(hsBr()))

        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Consistent)
        server.shutdown()
    }

    @Test
    fun `a pin store that cannot be read is storage_unavailable`() = runTest {
        val server = withoutPolicy().start()

        val report = verify(createVerifier(server, dataStoreFactory = UnavailablePreferenceDataStoreFactory()), answer(hsBr()))

        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Unverifiable(VerificationReason.STORAGE_UNAVAILABLE))
        assertThat(report.policy).isEqualTo(PolicyOutcome.NotPresent)
        server.shutdown()
    }

    @Test
    fun `overlapping runs leave the pin at the larger checkpoint`() = runTest {
        val rosterRequests = AtomicInteger()
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r1").toString()) }
            consistency = { first, second ->
                // The first run to fetch the roster holds the smaller checkpoint and gets its proof late.
                val response = ok(consistencyJson(first, second))
                if (second == 5) response.setBodyDelay(SLOW_PROOF_MILLIS, TimeUnit.MILLISECONDS) else response
            }
        }
        val server = resolver.start()
        val dataStoreFactory = CachingPreferenceDataStoreFactory()
        verify(createVerifier(server, dataStoreFactory = dataStoreFactory), answer(hsBr()))
        val smaller = resigned(withCheckpoint(servedRoster("r1"), size = 5)).toString()
        val larger = servedRoster("r2").toString()
        resolver.roster = { ok(if (rosterRequests.getAndIncrement() == 0) smaller else larger) }

        List(2) { async { createVerifier(server, dataStoreFactory = dataStoreFactory).verify(A_PHONE, answer(hsBr())) } }.awaitAll()

        val pin = ResolverCheckpointPin(dataStoreFactory.create(STORE_NAME)).read(server.url("/").toString())
        assertThat(pin).isEqualTo(LogCheckpoint(merkleRoot = rootAt(7), size = 7))
        resolver.roster = { ok(resigned(withCheckpoint(servedRoster("r1"), size = 6)).toString()) }
        val later = verify(createVerifier(server, dataStoreFactory = dataStoreFactory), answer(hsBr()))
        assertThat(later.logConsistency).isEqualTo(LogConsistencyOutcome.Rollback)
        server.shutdown()
    }
}
