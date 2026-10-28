/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.util.Base64

/**
 * GUA FORK: the resolver publishes the client-verification vectors and the `gua-lp.v1` vectors as the
 * contract every platform verifier is held to. Both files are byte-identical copies kept in the module's
 * own test resources, and every entry of every section is reproduced here and fails with its name.
 *
 * The keys are the RFC 8032 section 7.1 test constants, so the published signatures are reproducible;
 * signing here uses the JDK provider, which keeps the expected values independent of the Ed25519 the
 * app verifies with.
 */
class ResolverVerificationVectorsTest {
    private val vectors: JsonObject by lazy { VectorFixtures.load(VectorFixtures.CLIENT_VECTORS_RESOURCE) }
    private val lpVectors: JsonObject by lazy { VectorFixtures.load(VectorFixtures.LP_VECTORS_RESOURCE) }
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the files are present and name what they pin`() {
        assertThat(vectors.string("vectors")).isEqualTo("gua-client-verification.v1")
        assertThat(vectors.string("protocol")).isEqualTo("docs/verification/gua-resolver-verification-protocol.md")
        for (section in listOf("instants", "rosters", "rosterRejections", "policies", "policyRejections", "placements", "resolveAnswers")) {
            assertWithMessage(section).that(vectors[section]!!.jsonArray).isNotEmpty()
        }
        assertThat(vectors["keys"]!!.jsonObject).isNotEmpty()
        assertThat(lpVectors.string("encoding")).isEqualTo(CanonicalEncoder.ENCODING)
        for (section in listOf("primitives", "memberEntries", "rejections")) {
            assertWithMessage(section).that(lpVectors[section]!!.jsonArray).isNotEmpty()
        }
    }

    @Test
    fun `every key is an Ed25519 SubjectPublicKeyInfo around its raw public key`() {
        for ((name, element) in vectors["keys"]!!.jsonObject + lpVectors["keys"]!!.jsonObject) {
            val key = element.jsonObject
            val spki = Base64.getDecoder().decode(key.string("publicKeySpkiB64"))
            assertWithName(name, spki.size, 44)
            assertWithName(name, spki.copyOfRange(0, 12).toHex(), SPKI_PREFIX)
            assertWithName(name, spki.copyOfRange(12, 44).toHex(), key.string("publicKeyHex"))
            val raw = Ed25519Signatures.rawPublicKey(key.string("publicKeySpkiB64"))
            assertWithName(name, raw?.toHex(), key.string("publicKeyHex"))
            if (key.containsKey("seedHex")) {
                val message = byteArrayOf(1)
                val signature = VectorFixtures.sign(key.string("seedHex"), message)
                assertWithMessage(name).that(Ed25519Signatures.verify(raw!!, message, signature)).isTrue()
                assertWithMessage(name).that(Ed25519Signatures.verify(raw, byteArrayOf(2), signature)).isFalse()
            }
        }
    }

    @Test
    fun `every instant truncates to the listed epoch milliseconds`() {
        for (vector in vectors["instants"]!!.jsonArray.map { it.jsonObject }) {
            val iso = vector.string("iso")
            assertWithName(iso, iso.toEpochMillis(), vector.string("epochMs").toLong())
        }
    }

    @Test
    fun `every roster vector reproduces its bytes and verdict`() {
        for (vector in vectors["rosters"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val roster = json.decodeFromJsonElement<SignedRoster>(vector["roster"]!!)
            val form = rosterForm(vector.string("form"))
            assertWithName(name, CanonicalRoster.formOf(roster), form)

            val canonical = CanonicalRoster.bytes(roster, form)
            assertWithName(name, canonical.toString(Charsets.UTF_8), vector.string("canonicalUtf8"))
            assertWithName(name, canonical.toHex(), vector.string("canonicalHex"))
            assertWithName(name, MerkleTree.sha256Hex(canonical), vector.string("sha256Hex"))

            val verifier = RosterVerifier(trustedKeys(vector), vector.int("threshold"))
            val check = verifier.check(roster, form)
            assertWithName(name, check.valid, vector.int("validSignatures"))
            assertWithName(name, check.met, vector.bool("verified"))
            assertWithName(name, verifier.verify(roster), RosterSignatureOutcome.Valid)

            for ((keyId, keyRef) in vector["signatureKeyRefs"]!!.jsonObject) {
                val served = roster.authoritySignatures.first { it.authorityKeyId == keyId }.signatureB64
                assertWithName("$name, signature by $keyId", VectorFixtures.sign(seedOf(vectors, keyRef.jsonPrimitive.content), canonical), served)
            }
        }
    }

    @Test
    fun `every roster rejection is refused`() {
        for (vector in vectors["rosterRejections"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val roster = json.decodeFromJsonElement<SignedRoster>(vector["roster"]!!)
            if (vector["clientOnly"]?.jsonPrimitive?.boolean == true) {
                assertWithName(name, vector.string("form"), "mixed")
                assertWithName(name, CanonicalRoster.formOf(roster), RosterForm.MIXED)
                // Refused before any signature check, so no key is needed to reach the verdict.
                assertWithName(name, RosterVerifier(emptyList(), 1).verify(roster), RosterSignatureOutcome.Unverifiable(VerificationReason.MIXED_ROSTER_FORM))
                continue
            }
            val offeredForm = rosterForm(vector.string("form"))
            val canonical = CanonicalRoster.bytes(roster, offeredForm)
            assertWithName(name, canonical.toHex(), vector.string("canonicalHex"))
            assertWithName(name, MerkleTree.sha256Hex(canonical), vector.string("sha256Hex"))

            val verifier = RosterVerifier(trustedKeys(vector), vector.int("threshold"))
            val check = verifier.check(roster, offeredForm)
            assertWithName(name, check.valid, vector.int("validSignatures"))
            assertWithMessage(name).that(check.met).isFalse()
            assertWithMessage(name).that(vector.bool("verified")).isFalse()
            if (CanonicalRoster.formOf(roster) == offeredForm) {
                assertWithMessage(name).that(verifier.verify(roster)).isInstanceOf(RosterSignatureOutcome.Invalid::class.java)
            } else {
                // The offered form is the wrong one; the form the served JSON dictates still verifies.
                assertWithName(name, verifier.verify(roster), RosterSignatureOutcome.Valid)
            }
        }
    }

    @Test
    fun `the log vectors reproduce leaves, roots and consistency proofs`() {
        val log = vectors["log"]!!.jsonObject
        val leafHashes = log["events"]!!.jsonArray.map { it.jsonObject }.map { event ->
            val decoded = json.decodeFromJsonElement<LogEvent>(event)
            val name = "event ${decoded.index}"
            val leafData = decoded.leafData()
            assertWithName(name, leafData, event.string("leafDataUtf8"))
            val leafHash = MerkleTree.leafHash(leafData.toByteArray(Charsets.UTF_8))
            assertWithName(name, leafHash, event.string("leafHashHex"))
            leafHash
        }
        for (root in log["roots"]!!.jsonArray.map { it.jsonObject }) {
            val size = root.int("size")
            assertWithName("root at size $size", MerkleTree.root(leafHashes.take(size)), root.string("rootHex"))
        }
        for (proof in log["consistencyProofs"]!!.jsonArray.map { it.jsonObject }) {
            val name = "consistency ${proof.int("first")} to ${proof.int("second")}"
            assertWithMessage(name).that(proof.bool("valid")).isTrue()
            assertWithMessage(name).that(verifyConsistency(proof)).isTrue()
        }
        for (proof in log["invalidConsistencyProofs"]!!.jsonArray.map { it.jsonObject }) {
            val name = proof.string("name")
            assertWithMessage(name).that(proof.bool("valid")).isFalse()
            assertWithMessage(name).that(verifyConsistency(proof)).isFalse()
        }
    }

    @Test
    fun `every policy vector reproduces its bytes and verdict`() {
        val policies = vectors["policies"]!!.jsonArray.map { it.jsonObject }
        val rejections = vectors["policyRejections"]!!.jsonArray.map { it.jsonObject }
        for (vector in policies + rejections) {
            val name = vector.string("name")
            val bundle = json.decodeFromJsonElement<RoutingPolicyBundle>(vector["bundle"]!!)
            val canonical = CanonicalRoutingPolicy.bytes(bundle)
            assertWithName(name, canonical.toString(Charsets.UTF_8), vector.string("canonicalUtf8"))
            assertWithName(name, canonical.toHex(), vector.string("canonicalHex"))
            assertWithName(name, MerkleTree.sha256Hex(canonical), vector.string("sha256Hex"))
            for (zone in vector["delegatedRules"]!!.jsonArray.map { it.jsonObject }) {
                val zoneId = zone.string("zoneId")
                val delegated = CanonicalRoutingPolicy.delegatedRulesBytes(bundle.policyId, bundle.version, zoneId, bundle.rules)
                assertWithName("$name, zone $zoneId", delegated.toString(Charsets.UTF_8), zone.string("canonicalUtf8"))
                assertWithName("$name, zone $zoneId", delegated.toHex(), zone.string("canonicalHex"))
            }
            val verifier = RoutingPolicyVerifier(trustedKeys(vector), vector.int("threshold"))
            assertWithName(name, verifier.check(bundle).met, vector.bool("verified"))
            assertWithName(name, verifier.delegateVerifiedZones(bundle).sorted(), vector["delegateVerifiedZones"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
        for (vector in rejections) {
            assertWithMessage(vector.string("name")).that(vector.bool("verified")).isFalse()
        }
    }

    @Test
    fun `every placement vector reproduces the reference decision`() {
        val rosters = mapOf("r1" to rosterVector(0), "r2" to rosterVector(1))
        val policies = mapOf(
            "br-policy v3" to policyVector(0),
            "br-policy v3 without the sp-zone delegate signature" to policyVector(1),
        )
        for (vector in vectors["placements"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val rosterVector = rosters.getValue(vector.string("rosterRef"))
            val roster = json.decodeFromJsonElement<SignedRoster>(rosterVector["roster"]!!)
            assertWithName(name, RosterVerifier(trustedKeys(rosterVector), rosterVector.int("threshold")).verify(roster), RosterSignatureOutcome.Valid)

            val policyVector = vector["policyRef"]?.takeUnless { it is JsonNull }?.let { policies.getValue(it.jsonPrimitive.content) }
            val policy = policyVector?.let { json.decodeFromJsonElement<RoutingPolicyBundle>(it["bundle"]!!) }
            val zones = policyVector?.let { RoutingPolicyVerifier(trustedKeys(it), it.int("threshold")).delegateVerifiedZones(policy!!) }.orEmpty()

            val context = vector["context"]!!.jsonObject.let { c ->
                PlacementContext(
                    e164Phone = c.string("phone"),
                    country = c.stringOrNull("country"),
                    mccmnc = c.stringOrNull("mccmnc"),
                    carrier = c.stringOrNull("carrier"),
                    regionHint = c.stringOrNull("regionHint"),
                    affiliations = c["affiliations"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                    attributes = c["attributes"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
                )
            }
            val decision = PlacementEngine.decide(context, roster, policy, zones, vector.string("evaluatedAt").toEpochMillis())
            assertWithName(
                name,
                decision,
                PlacementDecision(vector.string("expectedHomeserverId"), vector.string("expectedRule"), vector.string("expectedRuleId")),
            )

            vector["fallback"]?.jsonObject?.let { fallback ->
                assertWithName(name, context.routingKey(), fallback.string("routingKeyUtf8"))
                assertWithName(name, roster.version, fallback.long("rosterVersion"))
                val accepting = roster.entries.filter { it.isActive && it.homeserver.acceptsNew }
                val candidates = accepting.map { it.homeserver }.sortedBy { it.id }
                val expectedCandidates = fallback["candidates"]!!.jsonArray.map { it.jsonObject }.map { it.string("id") to it.int("weight") }
                assertWithName(name, candidates.map { it.id to it.weight }, expectedCandidates)
                assertWithName(name, PlacementEngine.stableHash(context, roster.version, candidates), fallback.int("stableHash"))
            }
        }
    }

    @Test
    fun `every resolve answer vector locates as expected`() {
        val rosters = mapOf("r2" to rosterVector(1), "prod-2026-10-27" to rosterVector(3))
        for (vector in vectors["resolveAnswers"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val roster = json.decodeFromJsonElement<SignedRoster>(rosters.getValue(vector.string("rosterRef"))["roster"]!!)
            val answer = vector["answer"]!!.jsonObject
            val outcome = RosterVerifier.locate(answer.string("serverName"), answer.string("baseUrl"), answer.stringOrNull("masIssuer"), roster)
            assertWithName(name, outcome.code, vector.string("expected"))
        }
    }

    @Test
    fun `every lp primitive encodes to its bytes`() {
        for (vector in lpVectors["primitives"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val value = vector["value"]!!
            val encoder = CanonicalEncoder.raw()
            when (val type = vector.string("type")) {
                "string", "enum" -> encoder.string(value.jsonPrimitive.content)
                "int64" -> encoder.int64(value.jsonPrimitive.content.toLong())
                "bool" -> encoder.bool(value.jsonPrimitive.boolean)
                "optionalString" -> encoder.optionalString(if (value is JsonNull) null else value.jsonPrimitive.content)
                "stringSet" -> encoder.stringSet(value.jsonArray.map { it.jsonPrimitive.content })
                "stringList" -> encoder.stringList(value.jsonArray.map { it.jsonPrimitive.content })
                else -> error("$name: unknown primitive type $type")
            }
            assertWithName(name, encoder.toByteArray().toHex(), vector.string("hex"))
        }
    }

    @Test
    fun `every member entry vector reproduces its bytes and verifies`() {
        val verifier = MemberEntryVerifier()
        for (vector in lpVectors["memberEntries"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val homeserver = json.decodeFromJsonElement<RosterHomeserver>(vector["homeserver"]!!)
            val memberJson = vector["member"]!!.jsonObject
            val member = Json.decodeFromJsonElement<MemberAttestation>(memberJson)

            val canonical = CanonicalMemberEntry.bytes(homeserver, member)
            assertWithName(name, canonical.toHex(), vector.string("canonicalHex"))
            assertWithName(name, MerkleTree.sha256Hex(canonical), vector.string("sha256Hex"))
            assertWithName(name, member.notBefore.toEpochMillis(), vector.long("notBeforeEpochMs"))
            assertWithName(name, member.notAfter.toEpochMillis(), vector.long("notAfterEpochMs"))
            val own = member.signatures.first { it.keyId == member.keyId }.signatureB64
            assertWithName(name, VectorFixtures.sign(seedOf(lpVectors, vector.string("signingKeyRef")), canonical), own)

            val prior = memberPrior(vector)
            val notBefore = vector.long("notBeforeEpochMs")
            val notAfter = vector.long("notAfterEpochMs")
            assertWithName(name, verifier.verify(homeserver, memberJson, notBefore, prior), MemberEntryOutcome.Valid)
            assertWithName(name, verifier.verify(homeserver, memberJson, notAfter, prior), MemberEntryOutcome.Valid)
            assertWithName(name, verifier.verify(homeserver, memberJson, notBefore - 1, prior), MemberEntryOutcome.Invalid(VerificationReason.OUTSIDE_WINDOW))
            assertWithName(name, verifier.verify(homeserver, memberJson, notAfter + 1, prior), MemberEntryOutcome.Invalid(VerificationReason.OUTSIDE_WINDOW))
        }
    }

    @Test
    fun `every member entry rejection is refused by the named rule`() {
        val verifier = MemberEntryVerifier()
        val expectedReasons = mapOf(
            "duplicate search group" to VerificationReason.NO_CANONICAL_ENCODING,
            "baseUrl substituted under the member's original signature" to VerificationReason.BAD_SIGNATURE,
            "key rotation carrying only the new key's signature" to VerificationReason.KEY_CONTINUITY,
            "unknown field inside the signed sub-object" to VerificationReason.PARSE_ERROR,
            "sub-millisecond timestamp" to VerificationReason.BAD_WINDOW,
            "validity window longer than the 400 day maximum" to VerificationReason.BAD_WINDOW,
        )
        for (vector in lpVectors["rejections"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val homeserver = json.decodeFromJsonElement<RosterHomeserver>(vector["homeserver"]!!)
            val memberJson = vector["member"]!!.jsonObject
            val at = memberJson.string("notBefore").toEpochMillis()
            val outcome = verifier.verify(homeserver, memberJson, at, memberPrior(vector))
            assertWithName(name, outcome, MemberEntryOutcome.Invalid(expectedReasons.getValue(name)))
        }
    }

    @Test
    fun `an attested roster entry hashes to its MEMBER_ATTEST leaf`() {
        val roster = json.decodeFromJsonElement<SignedRoster>(rosterVector(1)["roster"]!!)
        val attestations = vectors["log"]!!.jsonObject["events"]!!.jsonArray.map { it.jsonObject }.filter { it.string("type") == "MEMBER_ATTEST" }
        assertThat(attestations).isNotEmpty()
        for (leaf in attestations) {
            val name = "MEMBER_ATTEST ${leaf.string("homeserverId")}"
            val entry = roster.entries.first { it.homeserver.id == leaf.string("homeserverId") }
            val member = Json.decodeFromJsonElement<MemberAttestation>(entry.member!!)
            assertWithName(name, MerkleTree.sha256Hex(CanonicalMemberEntry.bytes(entry.homeserver, member)), leaf.string("payloadHash"))
        }
    }

    private fun rosterVector(index: Int): JsonObject = vectors["rosters"]!!.jsonArray[index].jsonObject

    private fun policyVector(index: Int): JsonObject = vectors["policies"]!!.jsonArray[index].jsonObject

    private fun rosterForm(name: String): RosterForm = when (name) {
        "v1" -> RosterForm.V1
        "legacy" -> RosterForm.LEGACY
        "mixed" -> RosterForm.MIXED
        else -> error("unknown roster form $name")
    }

    private fun trustedKeys(vector: JsonObject): List<TrustedKey> = vector["trustedKeys"]!!.jsonArray.map { it.jsonObject }.map { key ->
        checkNotNull(TrustedKey.parse(key.string("id"), key.string("publicKeySpkiB64"))) { "${key.string("id")} is not an Ed25519 key" }
    }

    private fun memberPrior(vector: JsonObject): MemberPrior? = vector["previous"]?.jsonObject?.let {
        MemberPrior(keyId = it.string("keyId"), signingKey = it.string("signingKey"), sequence = it.long("sequence"), entryHash = it.string("entryHash"))
    }

    private fun verifyConsistency(proof: JsonObject): Boolean = MerkleTree.verifyConsistency(
        first = proof.long("first"),
        second = proof.long("second"),
        oldRoot = proof.string("oldRootHex"),
        newRoot = proof.string("newRootHex"),
        proof = proof["proof"]!!.jsonArray.map { it.jsonPrimitive.content },
    )

    private fun seedOf(file: JsonObject, keyRef: String): String = file["keys"]!!.jsonObject[keyRef]!!.jsonObject.string("seedHex")

    /** Asserts equality and names the failing vector, so a mismatch says which one broke. */
    private fun <T> assertWithName(name: String, actual: T, expected: T) {
        assertWithMessage(name).that(actual).isEqualTo(expected)
    }

    private companion object {
        private const val SPKI_PREFIX = "302a300506032b6570032100"
    }
}
