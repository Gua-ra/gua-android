/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

class DefaultResolverVerifierShapeTest {
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
    fun `an incomplete authority signature does not refuse the roster`() = runTest {
        val roster = servedRoster("r1")
        val incomplete = buildJsonObject { put("authorityKeyId", "authority-a") }
        val signatures = JsonArray(listOf(incomplete) + roster["authoritySignatures"]!!.jsonArray)
        val resolver = ScriptedResolver().apply { this.roster = { ok(JsonObject(roster + ("authoritySignatures" to signatures)).toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Valid)
        server.shutdown()
    }

    @Test
    fun `an incomplete authority signature counts as one that does not verify`() = runTest {
        val incomplete = JsonArray(listOf(buildJsonObject { put("authorityKeyId", "authority-a") }))
        val resolver = ScriptedResolver().apply { roster = { ok(JsonObject(servedRoster("r1") + ("authoritySignatures" to incomplete)).toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Invalid(VerificationReason.BAD_SIGNATURE))
        server.shutdown()
    }

    @Test
    fun `a checkpoint size past 31 bits is a parse error`() = runTest {
        val tooLarge = withCheckpoint(servedRoster("r1"), size = Int.MAX_VALUE + 1L, merkleRoot = rootAt(3))
        val resolver = ScriptedResolver().apply { roster = { ok(tooLarge.toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        server.shutdown()
    }

    @Test
    fun `a timestamp outside the resolver's form is a parse error`() = runTest {
        val roster = servedRoster("r1")
        val lowercase = JsonPrimitive(roster["issuedAt"]!!.jsonPrimitive.content.replace('T', 't'))
        val resolver = ScriptedResolver().apply { this.roster = { ok(JsonObject(roster + ("issuedAt" to lowercase)).toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
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
    fun `a member bound with a signed year is parse_error and the run goes on`() = runTest {
        val member = servedRoster("r1").rosterEntries().single()["member"]!!.jsonObject
        val farBound = JsonObject(member + ("notAfter" to JsonPrimitive("+300000000-01-01T00:00:00Z")))
        val resolver = ScriptedResolver().apply {
            roster = { ok(withMember(servedRoster("r1"), farBound).toString()) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.memberEntry).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.PARSE_ERROR))
        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.FirstSeen)
        assertThat(report.policy).isEqualTo(PolicyOutcome.NotPresent)
        server.shutdown()
    }

    @Test
    fun `a proof hash that is not 64 lowercase hex characters is a parse error`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(servedRoster("r1").toString()) } }
        val server = resolver.start()
        val verifier = createVerifier(server)
        verify(verifier, answer(hsBr()))

        resolver.roster = { ok(servedRoster("r2").toString()) }
        resolver.consistency = { first, second -> ok(consistencyJson(first, second, uppercase = true)) }
        val report = verify(verifier, answer(hsCa()))

        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        server.shutdown()
    }

    @Test
    fun `a roster number or boolean sent as a string is a parse error`() = runTest {
        val roster = servedRoster("r1")
        val entry = roster.rosterEntries().single()
        val homeserver = entry["homeserver"]!!.jsonObject
        val claim = buildJsonObject {
            put("country", "BR")
            put("priority", "1")
        }
        val quoted = listOf(
            JsonObject(roster + ("version" to JsonPrimitive("3"))),
            JsonObject(roster + ("logCheckpoint" to JsonObject(roster["logCheckpoint"]!!.jsonObject + ("size" to JsonPrimitive("3"))))),
            withEntry(roster, JsonObject(entry + ("homeserver" to JsonObject(homeserver + ("weight" to JsonPrimitive("5")))))),
            withEntry(roster, JsonObject(entry + ("homeserver" to JsonObject(homeserver + ("acceptsNew" to JsonPrimitive("true")))))),
            withEntry(roster, JsonObject(entry + ("claims" to JsonArray(listOf(claim))))),
        )
        for (body in quoted) {
            val server = ScriptedResolver().apply { this.roster = { ok(body.toString()) } }.start()

            val report = verify(createVerifier(server), answer(hsBr()))

            assertWithMessage(body.toString()).that(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
            server.shutdown()
        }
    }

    @Test
    fun `a roster field the contract never allows to be null or absent is a parse error`() = runTest {
        val roster = servedRoster("r1")
        val entry = roster.rosterEntries().single()
        val homeserver = entry["homeserver"]!!.jsonObject
        val broken = listOf("version", "issuedAt", "logCheckpoint", "entries", "authoritySignatures").flatMap { nullAndAbsent(roster, it) } +
            listOf("homeserver", "status").flatMap { field -> nullAndAbsent(entry, field).map { withEntry(roster, it) } } +
            listOf("weight", "acceptsNew").flatMap { field ->
                nullAndAbsent(homeserver, field).map { withEntry(roster, JsonObject(entry + ("homeserver" to it))) }
            }
        for (body in broken) {
            val server = ScriptedResolver().apply { this.roster = { ok(body.toString()) } }.start()

            val report = verify(createVerifier(server), answer(hsBr()))

            assertWithMessage(body.toString()).that(report.rosterSignature).isEqualTo(RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
            server.shutdown()
        }
    }

    @Test
    fun `a log whose events are null or absent is a parse error`() = runTest {
        for (body in nullAndAbsent(Json.parseToJsonElement(logJson()).jsonObject, "events")) {
            val server = withoutPolicy().apply { this.log = { ok(body.toString()) } }.start()

            val report = verify(createVerifier(server), answer(hsBr()))

            assertWithMessage(body.toString()).that(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
            server.shutdown()
        }
    }

    @Test
    fun `a log index sent as a string is a parse error`() = runTest {
        val events = logEvents().map { it.jsonObject }.mapIndexed { i, event -> if (i == 0) JsonObject(event + ("index" to JsonPrimitive("0"))) else event }
        val server = withoutPolicy().apply { log = { ok(logJson(events)) } }.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        server.shutdown()
    }

    @Test
    fun `a log event past the checkpoint must be well formed too`() = runTest {
        // r1 signs the first 3 leaves; the last event is past them.
        val events = logEvents().map { it.jsonObject }
        val badTime = JsonObject(events.last() + ("recordedAt" to JsonPrimitive(events.last()["recordedAt"]!!.jsonPrimitive.content.replace('T', 't'))))
        val server = withoutPolicy().apply { log = { ok(logJson(events.dropLast(1) + badTime)) } }.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        server.shutdown()
    }

    @Test
    fun `a consistency proof needs only its hashes`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(servedRoster("r1").toString()) } }
        val server = resolver.start()
        val verifier = createVerifier(server)
        verify(verifier, answer(hsBr()))

        resolver.roster = { ok(servedRoster("r2").toString()) }
        resolver.consistency = { first, second ->
            val proof = Json.parseToJsonElement(consistencyJson(first, second)).jsonObject
            ok(JsonObject(proof - "first" - "second").toString())
        }
        val report = verify(verifier, answer(hsCa()))

        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Consistent)
        server.shutdown()
    }

    @Test
    fun `a consistency document without a proof is a parse error`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(servedRoster("r1").toString()) } }
        val server = resolver.start()
        val verifier = createVerifier(server)
        verify(verifier, answer(hsBr()))

        resolver.roster = { ok(servedRoster("r2").toString()) }
        resolver.consistency = { first, second ->
            val noProof = buildJsonObject {
                put("first", first)
                put("second", second)
            }
            ok(noProof.toString())
        }
        val report = verify(verifier, answer(hsCa()))

        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
        server.shutdown()
    }

    @Test
    fun `a policy number or boolean sent as a string is a parse error`() = runTest {
        val bundle = servedPolicy(BR_POLICY)
        val rules = bundle["rules"]!!.jsonArray.map { it.jsonObject }
        fun withFirstRule(field: String, value: String) =
            JsonObject(bundle + ("rules" to JsonArray(listOf(JsonObject(rules.first() + (field to JsonPrimitive(value)))) + rules.drop(1))))
        val fallback = JsonObject(bundle["fallback"]!!.jsonObject + ("enabled" to JsonPrimitive("true")))
        val quoted = listOf(
            JsonObject(bundle + ("version" to JsonPrimitive("3"))),
            withFirstRule("priority", "1"),
            withFirstRule("enabled", "false"),
            JsonObject(bundle + ("fallback" to fallback)),
        )
        for (body in quoted) {
            val server = policyRoutingToBr().apply { policy = { ok(body.toString()) } }.start()

            val report = verify(createVerifier(server), answer(hsBr()))

            assertWithMessage(body.toString()).that(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR))
            server.shutdown()
        }
    }

    private fun nullAndAbsent(document: JsonObject, field: String) = listOf(JsonObject(document + (field to JsonNull)), JsonObject(document - field))

    private fun withEntry(roster: JsonObject, entry: JsonObject) = JsonObject(roster + ("entries" to JsonArray(listOf(entry))))
}
