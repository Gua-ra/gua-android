/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import io.element.android.libraries.guaresolver.genesis.AccountId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64

/**
 * GUA FORK: the cross-platform golden vectors of the authority chain (ADM-009, identity-service
 * `docs/specs/authority-vectors.v1.json`), checked against this client's own codec.
 *
 * WHY THE VECTORS RATHER THAN A ROUND TRIP. The server, this client and gua-ios each encode these records
 * independently, and a byte they disagree on is a signature that verifies on one side and not the other. A
 * round-trip test proves a codec agrees with itself; only a committed file that all three recompute proves
 * they agree with each other. The file is copied verbatim from identity-service, so a change on either side
 * shows up as a failing test rather than as a refused record in production.
 *
 * The records are rebuilt from the same inputs the vectors describe rather than sliced back out of the
 * expected bytes, and the chain is rebuilt in order, so the prevHash of each record is this client's own hash
 * of the previous one.
 */
class AuthorityVectorsTest {
    private val vectors: JsonObject = Json.parseToJsonElement(
        requireNotNull(javaClass.getResourceAsStream("/$VECTORS_RESOURCE")) {
            "$VECTORS_RESOURCE is missing from the test resources"
        }.bufferedReader().use { it.readText() }
    ).jsonObject

    private val challenge: ByteArray get() = hex(vectors.string("challengeHex"))

    private val accountReference: ByteArray
        get() = hex(vectors["account"]!!.jsonObject.string("rawBytesHex"))

    @Test
    fun `the accountId these records name re-encodes to the same 34 bytes`() {
        val account = vectors["account"]!!.jsonObject
        // The client never composes an accountId: it parses the one the server reported and signs over the
        // bytes of what it could re-encode. A vector whose id did not survive that would be signing over
        // something this client cannot name.
        assertThat(AccountId.parse(account.string("accountId")).rawBytes()).isEqualTo(accountReference)
    }

    @Test
    fun `every record vector is reproduced byte for byte`() {
        val expected = records()
        assertThat(expected).hasSize(6)
        expected.forEach { vector ->
            val built = build(vector.string("name"))
            assertThat(hexOf(built)).isEqualTo(vector.string("canonicalHex"))
            assertThat(built).hasLength(vector["length"]!!.jsonPrimitive.int)
            assertThat(hexOf(AuthorityRecordCodec.hash(built))).isEqualTo(vector.string("sha256Hex"))
            assertThat(vector.string("magic")).isEqualTo(typeOf(vector.string("name")).magic)
            assertThat(vector["seq"]!!.jsonPrimitive.long).isEqualTo(AuthorityRecordCodec.parse(built).seq)
        }
    }

    @Test
    fun `every preimage is the magic, the challenge and the canonical bytes`() {
        records().forEach { vector ->
            val name = vector.string("name")
            val preimage = AuthorityProofs.recordPreimage(typeOf(name), challenge, build(name))
            assertThat(hexOf(sha256(preimage))).isEqualTo(vector.string("preimageSha256Hex"))
        }
    }

    @Test
    fun `every signature is reproduced, because Ed25519 is deterministic`() {
        records().forEach { vector ->
            val name = vector.string("name")
            val preimage = AuthorityProofs.recordPreimage(typeOf(name), challenge, build(name))
            val seed = seedOf(vector.string("verifyingKeyHex"))
            val signature = Ed25519Sign(seed).sign(preimage)
            assertThat(Base64.getEncoder().encodeToString(signature)).isEqualTo(vector.string("signatureB64"))
        }
    }

    @Test
    fun `no signature verifies under another challenge`() {
        val otherChallenge = challenge.copyOf().also { it[0] = (it[0] + 1).toByte() }
        records().forEach { vector ->
            val name = vector.string("name")
            val record = build(name)
            val signature = Base64.getDecoder().decode(vector.string("signatureB64"))
            val verifier = Ed25519Verify(hex(vector.string("verifyingKeyHex")))
            // The challenge is inside the signature rather than checked beside it, which is what makes a
            // captured record useless: this is that property, asserted rather than asserted about.
            verifier.verify(signature, AuthorityProofs.recordPreimage(typeOf(name), challenge, record))
            var refused = false
            try {
                verifier.verify(signature, AuthorityProofs.recordPreimage(typeOf(name), otherChallenge, record))
            } catch (_: GeneralSecurityException) {
                refused = true
            }
            assertThat(refused).isTrue()
        }
    }

    @Test
    fun `every rejection is refused for the rule the vectors name`() {
        val rejections = vectors["rejections"]!!.jsonArray
        assertThat(rejections).hasSize(16)
        rejections.forEach { entry ->
            val rejection = entry.jsonObject
            val thrown = runCatching { AuthorityRecordCodec.parse(hex(rejection.string("hex"))) }.exceptionOrNull()
            assertThat(thrown).isInstanceOf(InvalidAuthorityRecordException::class.java)
            assertThat((thrown as InvalidAuthorityRecordException).reason)
                .isEqualTo(rejection.string("reason"))
        }
    }

    @Test
    fun `the record vectors are accepted by the same decoder that refuses the rejections`() {
        records().forEach { vector ->
            val parsed = AuthorityRecordCodec.parse(hex(vector.string("canonicalHex")))
            assertThat(parsed.type).isEqualTo(typeOf(vector.string("name")))
            assertThat(parsed.accountReference).isEqualTo(accountReference)
        }
    }

    // --- Rebuilding the chain ------------------------------------------------

    /**
     * The one record each vector describes, built from its inputs.
     *
     * The label and entropy constants are the values the vectors' own canonical bytes carry; everything
     * positional (the prevHash and the seq) comes from rebuilding the chain in order, so a client that hashed
     * a record differently would fail on the next one rather than on itself.
     */
    private fun build(name: String): ByteArray = when (name) {
        ADOPT -> adoptRoot()
        GRANT -> deviceGrant()
        REVOKE -> deviceRevoke()
        RECOVER_BY_KEY -> recoveryByCommittedKey()
        RECOVER_BY_ACCOUNT -> recoveryThroughAccountRecovery()
        OPPOSE -> oppose()
        else -> error("no builder for the vector named $name")
    }

    private fun adoptRoot(): ByteArray = AuthorityRecordCodec.adoptRoot(
        accountReference = accountReference,
        deviceKey = publicKey(TEST1),
        recoveryAuthorityKey = publicKey(TEST2),
        label = "iPhone",
        entropy = hex(ENTROPY_HEX),
    )

    private fun deviceGrant(): ByteArray = AuthorityRecordCodec.deviceGrant(
        accountReference = accountReference,
        prevHash = AuthorityRecordCodec.hash(adoptRoot()),
        seq = 2,
        granteeDeviceKey = publicKey(TEST3),
        label = "iPad",
        authorizingKey = publicKey(TEST1),
    )

    private fun deviceRevoke(): ByteArray = AuthorityRecordCodec.deviceRevoke(
        accountReference = accountReference,
        prevHash = AuthorityRecordCodec.hash(deviceGrant()),
        seq = 3,
        deviceKey = publicKey(TEST3),
        reason = AuthorityRecord.REASON_COMPROMISED,
        authorizingKey = publicKey(TEST1),
    )

    private fun recoveryByCommittedKey(): ByteArray = AuthorityRecordCodec.authorityRecovery(
        accountReference = accountReference,
        prevHash = AuthorityRecordCodec.hash(adoptRoot()),
        seq = 2,
        deviceKey = publicKey(TEST3),
        recoveryAuthorityKey = publicKey(TEST1),
        label = "iPad",
        authorization = AuthorityRecord.AUTHORIZATION_RECOVERY_KEY,
        authorizingKey = publicKey(TEST2),
        entropy = hex(ENTROPY_HEX),
    )

    private fun recoveryThroughAccountRecovery(): ByteArray = AuthorityRecordCodec.authorityRecovery(
        accountReference = accountReference,
        prevHash = AuthorityRecordCodec.hash(adoptRoot()),
        seq = 2,
        deviceKey = publicKey(TEST3),
        recoveryAuthorityKey = publicKey(TEST1),
        label = "iPad",
        authorization = AuthorityRecord.AUTHORIZATION_ACCOUNT_RECOVERY,
        authorizingKey = null,
        entropy = hex(ENTROPY_HEX),
    )

    private fun oppose(): ByteArray = AuthorityRecordCodec.oppose(
        accountReference = accountReference,
        // An Oppose carries the position of the record it cancels, which is why these two are the revocation's
        // own prevHash and seq rather than a place of its own.
        prevHash = AuthorityRecordCodec.hash(deviceGrant()),
        seq = 3,
        opposedRecordHash = AuthorityRecordCodec.hash(deviceRevoke()),
        authorizingKey = publicKey(TEST1),
    )

    private fun typeOf(name: String): AuthorityRecordType = when (name) {
        ADOPT -> AuthorityRecordType.ADOPT_ROOT
        GRANT -> AuthorityRecordType.DEVICE_GRANT
        REVOKE -> AuthorityRecordType.DEVICE_REVOKE
        RECOVER_BY_KEY, RECOVER_BY_ACCOUNT -> AuthorityRecordType.AUTHORITY_RECOVERY
        OPPOSE -> AuthorityRecordType.OPPOSE
        else -> error("no type for the vector named $name")
    }

    private fun records(): List<JsonObject> = (vectors["records"] as JsonArray).map { it.jsonObject }

    private fun keys(): JsonObject = vectors["keys"]!!.jsonObject

    private fun publicKey(name: String): ByteArray = hex(keys()[name]!!.jsonObject.string("publicKeyHex"))

    /** The seed whose public half is [publicKeyHex], so a vector names its signer rather than the test. */
    private fun seedOf(publicKeyHex: String): ByteArray {
        val entry = keys().entries.first { it.value.jsonObject.string("publicKeyHex") == publicKeyHex }
        return hex(entry.value.jsonObject.string("seedHex"))
    }

    private fun JsonObject.string(field: String): String = this[field]!!.jsonPrimitive.content

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

    private fun hexOf(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }

    private companion object {
        const val VECTORS_RESOURCE = "authority-vectors.v1.json"
        const val TEST1 = "rfc8032-test1"
        const val TEST2 = "rfc8032-test2"
        const val TEST3 = "rfc8032-test3"

        /** The 16 entropy bytes the vectors' records carry. */
        const val ENTROPY_HEX = "f0e1d2c3b4a5968778695a4b3c2d1e0f"

        const val ADOPT = "AdoptRoot, device test1, recovery test2"
        const val GRANT = "DeviceGrant of test3, authorized by test1"
        const val REVOKE = "DeviceRevoke of test3, authorized by test1, reason compromised"
        const val RECOVER_BY_KEY = "AuthorityRecovery under the committed recovery key test2"
        const val RECOVER_BY_ACCOUNT = "AuthorityRecovery through account recovery, signed by the device it installs"
        const val OPPOSE = "Oppose the revocation above, authorized by test1"
    }
}
