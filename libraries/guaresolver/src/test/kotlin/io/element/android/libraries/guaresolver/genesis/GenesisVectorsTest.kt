/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * GUA FORK: the published golden vectors are the contract, not prose (ADM-008). identity-service ships
 * them at docs/specs/genesis-vectors.v1.json and this is a byte-identical copy, kept in the module's own
 * test resources so the test is hermetic.
 *
 * Every byte of them is recomputed here: canonical bytes, object hashes, accountIds, the two
 * deterministic proof signatures, and every case a conforming decoder must refuse together with the rule
 * that refuses it.
 *
 * The keys are the RFC 8032 section 7.1 test constants, which is what makes the signatures reproducible.
 * They are published values and sign nothing real. Signing here uses the JDK provider (Ed25519 is JDK
 * 15+ and the tests run on 21), which keeps the expected values independent of the Ed25519 the app
 * itself signs with.
 */
class GenesisVectorsTest {
    private val vectors: JsonObject by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(VECTORS_RESOURCE)) {
            "$VECTORS_RESOURCE is missing from the test resources"
        }
        Json.parseToJsonElement(stream.reader().readText()).jsonObject
    }

    @Test
    fun `the file is present and names the decision it implements`() {
        assertThat(vectors.string("encoding")).isEqualTo("gua-account-objects.v1")
        assertThat(vectors.string("decision")).contains("ADM-008")
        assertThat(vectors["accountGenesis"]!!.jsonArray).isNotEmpty()
        assertThat(vectors["bootstrapGenesis"]!!.jsonArray).isNotEmpty()
    }

    @Test
    fun `every AccountGenesis vector reproduces`() {
        for (vector in vectors["accountGenesis"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val canonical = vector.string("canonicalHex").hexToBytes()

            val encoded = AccountGenesisCodec.encode(
                authorityPublicKey = vector.string("authorityPublicKeyHex").hexToBytes(),
                recoveryFrameworkId = vector.int("recoveryFrameworkId"),
                recoveryAuthorityPublicKey = vector.string("recoveryAuthorityPublicKeyHex").hexToBytes(),
                entropy = vector.string("entropyHex").hexToBytes(),
            )
            assertWithName(name, encoded.toHex(), vector.string("canonicalHex"))

            val genesis = AccountGenesisCodec.decode(canonical)
            assertWithName(name, genesis.genesisVersion, vector.int("genesisVersion"))
            assertWithName(name, genesis.suite, vector.int("suite"))
            assertWithName(name, genesis.recoveryFrameworkId, vector.int("recoveryFrameworkId"))
            assertWithName(name, genesis.authorityPublicKey().toHex(), vector.string("authorityPublicKeyHex"))
            assertWithName(name, genesis.recoveryAuthorityPublicKey().toHex(), vector.string("recoveryAuthorityPublicKeyHex"))
            assertWithName(name, genesis.entropy().toHex(), vector.string("entropyHex"))
            assertWithName(name, sha256(canonical).toHex(), vector.string("sha256Hex"))
            assertWithName(name, genesis.accountId().value, vector.string("accountId"))
            assertWithName(name, genesis.accountId().isGenesisRooted, true)

            // Deterministic Ed25519: the published registration proof is reproducible.
            val seed = seedOf(vector.string("authorityPublicKeyHex"))
            val signature = sign(seed, GenesisProofs.genesisProofPreimage(canonical))
            assertWithName(name, signature, vector.string("genesisProofSignatureB64"))
        }
    }

    @Test
    fun `every BootstrapGenesis vector reproduces`() {
        for (vector in vectors["bootstrapGenesis"]!!.jsonArray.map { it.jsonObject }) {
            val name = vector.string("name")
            val canonical = vector.string("canonicalHex").hexToBytes()

            assertWithName(name, BootstrapGenesisCodec.encode(vector.string("entropyHex").hexToBytes()).toHex(), vector.string("canonicalHex"))

            val genesis = BootstrapGenesisCodec.decode(canonical)
            assertWithName(name, genesis.version, vector.int("version"))
            assertWithName(name, genesis.suite, vector.int("suite"))
            assertWithName(name, sha256(canonical).toHex(), vector.string("sha256Hex"))
            assertWithName(name, genesis.accountId().value, vector.string("accountId"))
            assertWithName(name, genesis.accountId().rootClass, AccountId.CLASS_BOOTSTRAP)
        }
    }

    @Test
    fun `the attach proof vector reproduces`() {
        val vector = vectors["attachProof"]!!.jsonObject

        assertThat(vector.string("domain")).isEqualTo(GenesisProofs.ATTACH_PROOF_DOMAIN)
        assertThat(vector.int("domainLength")).isEqualTo(GenesisProofs.ATTACH_PROOF_DOMAIN.length)
        assertThat(vector.int("domainLength")).isEqualTo(27)
        assertThat(vector.int("preimageLength")).isEqualTo(GenesisProofs.ATTACH_PREIMAGE_LENGTH)
        assertThat(vector.int("preimageLength")).isEqualTo(93)

        val accountId = AccountId.parse(vector.string("accountId"))
        assertThat(accountId.rawBytes().toHex()).isEqualTo(vector.string("accountIdRawHex"))

        val challenge = vector.string("challengeHex").hexToBytes()
        assertThat(challenge.size).isEqualTo(GenesisProofs.ATTACH_CHALLENGE_LENGTH)
        val preimage = GenesisProofs.attachProofPreimage(challenge, accountId)
        assertThat(preimage.toHex()).isEqualTo(vector.string("preimageHex"))

        val genesisVector = vectors["accountGenesis"]!!.jsonArray.first().jsonObject
        val seed = seedOf(genesisVector.string("authorityPublicKeyHex"))
        assertThat(sign(seed, preimage)).isEqualTo(vector.string("signatureB64"))
    }

    @Test
    fun `the accountId rules match the implementation`() {
        val rules = vectors["accountId"]!!.jsonObject

        assertThat(rules.string("pattern")).isEqualTo(AccountId.CANONICAL_PATTERN)
        assertThat(rules.string("prefix")).isEqualTo(AccountId.PREFIX)
        assertThat(rules.int("formatVersion")).isEqualTo(AccountId.FORMAT_VERSION.toInt())
        assertThat(rules.int("rootClassGenesis")).isEqualTo(AccountId.CLASS_GENESIS.toInt())
        assertThat(rules.int("rootClassBootstrap")).isEqualTo(AccountId.CLASS_BOOTSTRAP.toInt())
        assertThat(rules.int("rawLength")).isEqualTo(AccountId.RAW_LENGTH)
        assertThat(rules.int("encodedLength")).isEqualTo(AccountId.ENCODED_LENGTH)
        assertThat(rules.int("totalLength")).isEqualTo(AccountId.LENGTH)
        assertThat(rules["allowedFinalCharacters"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("a", "i", "q", "y")
    }

    @Test
    fun `every published AccountGenesis rejection is refused by the named rule`() {
        for (rejection in rejections("accountGenesis")) {
            val name = rejection.string("name")
            val thrown = runCatchingExceptions { AccountGenesisCodec.decode(rejection.string("hex").hexToBytes()) }.exceptionOrNull()
            assertWithName(name, (thrown as? InvalidGenesisException)?.reason, rejection.string("reason"))
        }
    }

    @Test
    fun `every published BootstrapGenesis rejection is refused by the named rule`() {
        for (rejection in rejections("bootstrapGenesis")) {
            val name = rejection.string("name")
            val thrown = runCatchingExceptions { BootstrapGenesisCodec.decode(rejection.string("hex").hexToBytes()) }.exceptionOrNull()
            assertWithName(name, (thrown as? InvalidGenesisException)?.reason, rejection.string("reason"))
        }
    }

    @Test
    fun `every published accountId rejection is refused by the named rule`() {
        for (rejection in rejections("accountId")) {
            val name = rejection.string("name")
            val thrown = runCatchingExceptions { AccountId.parse(rejection.string("value")) }.exceptionOrNull()
            assertWithName(name, (thrown as? InvalidGenesisException)?.reason, rejection.string("reason"))
        }
    }

    private fun rejections(group: String): List<JsonObject> =
        (vectors["rejections"]!!.jsonObject[group] as JsonArray).map { it.jsonObject }

    /** Asserts equality and names the failing vector, so a mismatch says which one broke. */
    private fun <T> assertWithName(name: String, actual: T, expected: T) {
        assertWithMessage(name).that(actual).isEqualTo(expected)
    }

    private fun seedOf(publicKeyHex: String): String {
        val keys = vectors["keys"]!!.jsonObject
        for ((_, key) in keys) {
            val entry = key.jsonObject
            if (entry.string("publicKeyHex") == publicKeyHex) return entry.string("seedHex")
        }
        error("the vectors name a key they do not publish")
    }

    /** Signs with the JDK Ed25519 provider, independent of what the app signs with. */
    private fun sign(seedHex: String, message: ByteArray): String {
        val privateKey = KeyFactory.getInstance("Ed25519")
            .generatePrivate(PKCS8EncodedKeySpec((PKCS8_PREFIX + seedHex).hexToBytes()))
        return Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(message)
            Base64.getEncoder().encodeToString(sign())
        }
    }

    private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    private companion object {
        private const val VECTORS_RESOURCE = "genesis-vectors.v1.json"
        private const val PKCS8_PREFIX = "302e020100300506032b657004220420"

        private fun JsonObject.string(key: String): String = this[key]!!.jsonPrimitive.content
        private fun JsonObject.int(key: String): Int = this[key]!!.jsonPrimitive.content.toInt()
        private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
