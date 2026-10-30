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
import java.nio.ByteBuffer
import java.util.Base64

/**
 * The resolver's published vectors, byte-identical copies in this module's test resources. Signing uses the JDK
 * provider, so expected values do not depend on the Ed25519 the app verifies with.
 */
class ResolverVerificationVectorsTest {
    private val vectors: JsonObject by lazy { VectorFixtures.load(VectorFixtures.CLIENT_VECTORS_RESOURCE) }
    private val lpVectors: JsonObject by lazy { VectorFixtures.load(VectorFixtures.LP_VECTORS_RESOURCE) }
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the files are present and name what they pin`() {
        assertThat(vectors.string("vectors")).isEqualTo("gua-client-verification.v1")
        assertThat(vectors.string("protocol")).isEqualTo("docs/verification/gua-resolver-verification-protocol.md")
        val sections = listOf(
            "instants",
            "rosters",
            "rosterRejections",
            "policies",
            "policyRejections",
            "policyValidity",
            "windows",
            "policyLog",
            "placements",
            "resolveAnswers",
        )
        for (section in sections) {
            assertWithMessage(section).that(vectors[section]!!.jsonArray).isNotEmpty()
        }
        assertThat(vectors["keys"]!!.jsonObject).isNotEmpty()
        assertThat(lpVectors.string("encoding")).isEqualTo("gua-lp.v1")
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
            assertWithName(name, CanonicalRoster.formOf(vector["roster"]!!.jsonObject), form)

            val canonical = CanonicalRoster.bytes(roster, form)
            assertWithName(name, canonical.toString(Charsets.UTF_8), vector.string("canonicalUtf8"))
            assertWithName(name, canonical.toHex(), vector.string("canonicalHex"))
            assertWithName(name, MerkleTree.sha256Hex(canonical), vector.string("sha256Hex"))

            val verifier = RosterVerifier(trustedKeys(vector), vector.int("threshold"))
            val check = verifier.check(roster, form)
            assertWithName(name, check.valid, vector.int("validSignatures"))
            assertWithName(name, check.met, vector.bool("verified"))
            assertWithName(name, verifier.verify(roster, form), RosterSignatureOutcome.Valid)

            for ((keyId, keyRef) in vector["signatureKeyRefs"]?.jsonObject.orEmpty()) {
                val served = roster.authoritySignatures.first { it.authorityKeyId == keyId }.signatureB64
                assertWithName("$name, signature by $keyId", VectorFixtures.sign(seedOf(vectors, keyRef.jsonPrimitive.content), canonical), served)
            }
        }
    }

    @Test
    fun `every roster rejection is refused with its expected outcome`() {
        for (vector in vectors["rosterRejections"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val roster = json.decodeFromJsonElement<SignedRoster>(vector["roster"]!!)
            // Each case names the form to check it with, which is not always the one its JSON implies.
            val form = rosterForm(vector.string("form"))
            if (form == RosterForm.MIXED) {
                assertWithName(name, CanonicalRoster.formOf(vector["roster"]!!.jsonObject), RosterForm.MIXED)
                // Refused before any signature check, so no key is needed to reach the verdict.
                assertWithName(name, RosterVerifier(emptyList(), 1).verify(roster, form).text, vector.string("expectedOutcome"))
                continue
            }
            val verifier = RosterVerifier(trustedKeys(vector), vector.int("threshold"))
            assertWithName(name, verifier.verify(roster, form).text, vector.string("expectedOutcome"))
            val canonical = CanonicalRoster.bytes(roster, form)
            assertWithName(name, canonical.toHex(), vector.string("canonicalHex"))
            assertWithName(name, MerkleTree.sha256Hex(canonical), vector.string("sha256Hex"))
            assertWithName(name, verifier.check(roster, form).valid, vector.int("validSignatures"))
            assertWithMessage(name).that(vector.bool("verified")).isFalse()
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
            assertWithName(name, verifier.verify(bundle).text, vector.stringOrNull("expectedOutcome") ?: PolicyOutcome.Valid.code)
        }
        for (vector in rejections) {
            assertWithMessage(vector.string("name")).that(vector.bool("verified")).isFalse()
        }
    }

    @Test
    fun `every policy log vector reaches its outcome against the whole log`() {
        val events = vectors["log"]!!.jsonObject["events"]!!.jsonArray.map { json.decodeFromJsonElement<LogEvent>(it) }
        for (vector in vectors["policyLog"]!!.jsonArray.map { it.jsonObject }) {
            val served = (vector["servedPolicy"] as? JsonObject)?.let { LoggedPolicy(it.stringOrNull("policyId"), it.long("version"), it.string("sha256Hex")) }
            val checkpoint = vector["checkpoint"]!!.jsonObject.let { LogCheckpoint(merkleRoot = it.string("rootHex"), size = it.long("size")) }
            assertWithName(vector.string("name"), PolicyLog.check(served, checkpoint, events).text, vector.string("expected"))
        }
    }

    @Test
    fun `every placement vector reproduces the reference decision`() {
        for (vector in vectors["placements"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val rosterVector = vectors.byRef("rosters", vector.string("rosterRef"))
            val roster = json.decodeFromJsonElement<SignedRoster>(rosterVector["roster"]!!)
            val rosterVerifier = RosterVerifier(trustedKeys(rosterVector), rosterVector.int("threshold"))
            assertWithName(name, rosterVerifier.verify(roster, rosterForm(rosterVector.string("form"))), RosterSignatureOutcome.Valid)

            val policyVector = vector.stringOrNull("policyRef")?.let { vectors.byRef("policies", it) }
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
            val evaluatedAt = vector.string("evaluatedAt").toEpochMillis()
            val decision = PlacementEngine.decide(context, roster, policy, zones, evaluatedAt)
            assertWithName(
                name,
                decision,
                PlacementDecision(vector.string("expectedHomeserverId"), vector.string("expectedRule"), vector.string("expectedRuleId")),
            )
            val unsignedRuleAbove = policy?.let { PlacementEngine.unsignedRuleOutranks(context, it, zones, decision, evaluatedAt) } ?: false
            assertWithName(name, unsignedRuleAbove, vector.bool("unsignedRuleAbove"))

            vector["fallback"]?.jsonObject?.let { fallback ->
                // Claims nobody verified never reach the routing key.
                val placed = context.withoutUnverifiedClaims()
                assertWithName(name, placed.routingKey(), fallback.string("routingKeyUtf8"))
                assertWithName(name, roster.version, fallback.long("rosterVersion"))
                val accepting = roster.entries.filter { it.isActive && it.homeserver.acceptsNew }
                val candidates = accepting.map { it.homeserver }.sortedBy { it.id }
                val expectedCandidates = fallback["candidates"]!!.jsonArray.map { it.jsonObject }.map { it.string("id") to it.int("weight") }
                assertWithName(name, candidates.map { it.id to it.weight }, expectedCandidates)
                assertWithName(name, PlacementEngine.stableHash(placed, roster.version, candidates), fallback.int("stableHash"))
            }
        }
    }

    @Test
    fun `every policy validity vector is in effect exactly inside the bundle window`() {
        for (vector in vectors["policyValidity"]!!.jsonArray.map { it.jsonObject }) {
            val bundle = json.decodeFromJsonElement<RoutingPolicyBundle>(vectors.byRef("policies", vector.string("policyRef"))["bundle"]!!)
            assertWithName(vector.string("name"), bundle.isInEffectAt(vector.string("evaluatedAt").toEpochMillis()), vector.bool("inEffect"))
        }
    }

    @Test
    fun `a null endpoint field is the text null in the roster bytes, as the resolver writes it`() {
        val homeserver = RosterHomeserver(id = null, serverName = null, baseUrl = null, masIssuer = null, weight = 0, acceptsNew = false)
        val roster = SignedRoster(
            version = 1,
            issuedAt = "2026-10-01T00:00:00Z",
            entries = listOf(SignedRosterEntry(homeserver = homeserver, status = "ACTIVE")),
            logCheckpoint = LogCheckpoint(merkleRoot = "00", size = 0),
            authoritySignatures = emptyList(),
        )

        val entryLine = CanonicalRoster.bytes(roster, RosterForm.LEGACY).toString(Charsets.UTF_8).lines()[4]

        assertThat(entryLine).startsWith(listOf("null", "null", "null", "null", "").joinToString("\u001f"))
    }

    @Test
    fun `every window vector is in effect exactly inside its bounds, for a bundle and for a zone`() {
        for (vector in vectors["windows"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val notBefore = vector.stringOrNull("notBefore")
            val expiresAt = vector.stringOrNull("expiresAt")
            val at = vector.string("evaluatedAt").toEpochMillis()
            assertWithName(name, RoutingPolicyBundle(notBefore = notBefore, expiresAt = expiresAt).isInEffectAt(at), vector.bool("inEffect"))
            assertWithName(name, DelegationZone(notBefore = notBefore, expiresAt = expiresAt).isInEffectAt(at), vector.bool("inEffect"))
        }
    }

    @Test
    fun `every resolve answer vector locates the expected entry`() {
        for (vector in vectors["resolveAnswers"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val roster = json.decodeFromJsonElement<SignedRoster>(vectors.byRef("rosters", vector.string("rosterRef"))["roster"]!!)
            val answer = vector["answer"]!!.jsonObject
            val located = RosterVerifier.locate(answer.string("serverName"), answer.string("baseUrl"), answer.stringOrNull("masIssuer"), roster)
            assertWithName(name, located.match.code, vector.string("expected"))
            assertWithName(name, located.entry?.homeserver?.id, vector.stringOrNull("expectedEntryId"))
        }
    }

    @Test
    fun `every lp primitive encodes to its bytes`() {
        for (vector in lpVectors["primitives"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val value = vector["value"]!!
            val bytes = when (val type = vector.string("type")) {
                "string", "enum" -> CanonicalEncoder().string(value.jsonPrimitive.content).toByteArray()
                "int64" -> CanonicalEncoder().int64(value.jsonPrimitive.content.toLong()).toByteArray()
                "optionalString" -> CanonicalEncoder().optionalString(if (value is JsonNull) null else value.jsonPrimitive.content).toByteArray()
                "stringSet" -> CanonicalEncoder().stringSet(value.jsonArray.map { it.jsonPrimitive.content }).toByteArray()
                // No signed object uses these two yet, so they are written out here: one byte, and a count then strings.
                "bool" -> byteArrayOf(if (value.jsonPrimitive.boolean) 1 else 0)
                "stringList" -> value.jsonArray.fold(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.jsonArray.size).array()) { out, element ->
                    out + CanonicalEncoder().string(element.jsonPrimitive.content).toByteArray()
                }
                else -> error("$name: unknown primitive type $type")
            }
            assertWithName(name, bytes.toHex(), vector.string("hex"))
        }
    }

    @Test
    fun `every member entry vector reproduces its bytes and verifies`() {
        val verifier = MemberEntryVerifier
        for (vector in lpVectors["memberEntries"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val homeserver = json.decodeFromJsonElement<RosterHomeserver>(vector["homeserver"]!!.jsonObject.withRosterAttributes())
            val memberJson = vector["member"]!!.jsonObject
            val member = Json.decodeFromJsonElement<MemberAttestation>(memberJson)

            val canonical = CanonicalMemberEntry.bytes(homeserver, member)
            assertWithName(name, canonical.toHex(), vector.string("canonicalHex"))
            assertWithName(name, MerkleTree.sha256Hex(canonical), vector.string("sha256Hex"))
            assertWithName(name, member.notBefore!!.toEpochMillis(), vector.long("notBeforeEpochMs"))
            assertWithName(name, member.notAfter!!.toEpochMillis(), vector.long("notAfterEpochMs"))
            val own = member.signatures!!.first { it.keyId == member.keyId }.signatureB64
            assertWithName(name, VectorFixtures.sign(seedOf(lpVectors, vector.string("signingKeyRef")), canonical), own)

            val notBefore = vector.long("notBeforeEpochMs")
            val notAfter = vector.long("notAfterEpochMs")
            assertWithName(name, verifier.verify(homeserver, memberJson, notBefore), MemberEntryOutcome.Valid)
            assertWithName(name, verifier.verify(homeserver, memberJson, notAfter), MemberEntryOutcome.Valid)
            assertWithName(name, verifier.verify(homeserver, memberJson, notBefore - 1), MemberEntryOutcome.Invalid(VerificationReason.OUTSIDE_WINDOW))
            assertWithName(name, verifier.verify(homeserver, memberJson, notAfter + 1), MemberEntryOutcome.Invalid(VerificationReason.OUTSIDE_WINDOW))
        }
    }

    @Test
    fun `every member entry rejection is refused by the named rule`() {
        val verifier = MemberEntryVerifier
        val expectedReasons = mapOf(
            "duplicate search group" to VerificationReason.NO_CANONICAL_ENCODING,
            "baseUrl substituted under the member's original signature" to VerificationReason.BAD_SIGNATURE,
            "unknown field inside the signed sub-object" to VerificationReason.PARSE_ERROR,
            "sub-millisecond timestamp" to VerificationReason.BAD_WINDOW,
            "validity window longer than the 400 day maximum" to VerificationReason.BAD_WINDOW,
        )
        // A rotation vector needs the previous entry, and v1 keeps none.
        for (vector in lpVectors["rejections"]!!.jsonArray.map { it.jsonObject }.filter { "previous" !in it }) {
            val name = vector.string("name")
            val homeserver = json.decodeFromJsonElement<RosterHomeserver>(vector["homeserver"]!!.jsonObject.withRosterAttributes())
            val memberJson = vector["member"]!!.jsonObject
            val at = memberJson.string("notBefore").toEpochMillis()
            assertWithName(name, verifier.verify(homeserver, memberJson, at), MemberEntryOutcome.Invalid(expectedReasons.getValue(name)))
        }
    }

    @Test
    fun `an attested roster entry hashes to its MEMBER_ATTEST leaf`() {
        val roster = json.decodeFromJsonElement<SignedRoster>(vectors.byRef("rosters", "r2")["roster"]!!)
        val attestations = vectors["log"]!!.jsonObject["events"]!!.jsonArray.map { it.jsonObject }.filter { it.string("type") == "MEMBER_ATTEST" }
        assertThat(attestations).isNotEmpty()
        for (leaf in attestations) {
            val name = "MEMBER_ATTEST ${leaf.string("homeserverId")}"
            val entry = roster.entries.first { it.homeserver.id == leaf.string("homeserverId") }
            val member = Json.decodeFromJsonElement<MemberAttestation>(entry.member!!)
            assertWithName(name, MerkleTree.sha256Hex(CanonicalMemberEntry.bytes(entry.homeserver, member)), leaf.string("payloadHash"))
        }
    }

    private fun rosterForm(name: String): RosterForm = when (name) {
        "v1" -> RosterForm.V1
        "legacy" -> RosterForm.LEGACY
        "mixed" -> RosterForm.MIXED
        else -> error("unknown roster form $name")
    }

    private fun trustedKeys(vector: JsonObject): List<TrustedKey> = vector["trustedKeys"]!!.jsonArray.map { it.jsonObject }.map { key ->
        checkNotNull(TrustedKey.parse(key.string("id"), key.string("publicKeySpkiB64"))) { "${key.string("id")} is not an Ed25519 key" }
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
