/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import io.element.android.libraries.guaresolver.genesis.Ed25519PublicKeys
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * GUA FORK: builds the canonical bytes of the authority records this client submits (ADM-009 decision 2).
 * Kotlin side of the identity-service `AuthorityRecordCodec`, with the same offsets and the same refusal
 * tokens.
 *
 * Only the two records a phone signs are built here: `AdoptRoot`, which roots a bootstrap account on this
 * device, and `DeviceGrant`, which gives another device authority. `DeviceRevoke` and `AuthorityRecovery`
 * have their magic and their length in [AuthorityRecordType] and no builder, because a builder for a
 * transition with no screen behind it would be a claim this client cannot keep.
 *
 * WHY THE CLIENT VALIDATES WHAT THE SERVER WILL VALIDATE AGAIN. A record refused by the server's decoder
 * costs a burned challenge, and a burned challenge costs another step-up. Checking the same rules before
 * spending one turns a refusal the user would have to repeat into a bug that fails in a test. The rules are
 * ADM-008 decision 1's, carried forward: an all-zero key is checked separately from point decoding, because
 * the all-zero encoding decodes to a valid low-order point.
 */
object AuthorityRecordCodec {
    private val random = SecureRandom()

    private const val OFFSET_VERSION = 4
    private const val OFFSET_SUITE = 5
    private const val OFFSET_ACCOUNT = 6
    private const val OFFSET_PREV_HASH = 40
    private const val OFFSET_SEQ = 72
    private const val OFFSET_BODY = 80

    // AdoptRoot body, from OFFSET_BODY.
    private const val ADOPT_DEVICE_KEY = OFFSET_BODY
    private const val ADOPT_FRAMEWORK = 112
    private const val ADOPT_RECOVERY_KEY = 113
    private const val ADOPT_LABEL = 145
    private const val ADOPT_ENTROPY = 161

    // DeviceGrant body.
    private const val GRANT_DEVICE_KEY = OFFSET_BODY
    private const val GRANT_FLAGS = 112
    private const val GRANT_LABEL = 113
    private const val GRANT_AUTHORIZING_KEY = 129

    /**
     * The canonical bytes of an `AdoptRoot` (`GUAA`, 177 bytes), which sits at `seq = 1` on an empty chain.
     *
     * @param accountReference the 34 raw accountId bytes, read from `GET /account/authority` and never
     * composed locally: decision 3 rule 2 has the server resolve the account from its own session state, so
     * a record built for another account is refused whatever endpoint it arrives at.
     * @param deviceKey this device's authority public key, generated on device and non-synced.
     * @param recoveryAuthorityKey the recovery authority public key framework 0x01 commits beside it.
     * @param label at most 16 bytes of UTF-8. It is what a notification is allowed to name.
     * @param entropy 16 CSPRNG bytes, so two devices that somehow minted the same keys still differ.
     */
    fun adoptRoot(
        accountReference: ByteArray,
        deviceKey: ByteArray,
        recoveryAuthorityKey: ByteArray,
        label: String,
        entropy: ByteArray = randomEntropy(),
    ): ByteArray {
        requireKey(deviceKey, "device_key")
        requireKey(recoveryAuthorityKey, "recovery_key")
        requireDistinct(deviceKey, recoveryAuthorityKey)
        requireLength(entropy, AuthorityRecord.ENTROPY_LENGTH, "entropy")

        val body = ByteArray(AuthorityRecordType.ADOPT_ROOT.bodyLength)
        deviceKey.copyInto(body, ADOPT_DEVICE_KEY - OFFSET_BODY)
        body[ADOPT_FRAMEWORK - OFFSET_BODY] = AuthorityRecord.RECOVERY_FRAMEWORK_COMMITTED_KEY.toByte()
        recoveryAuthorityKey.copyInto(body, ADOPT_RECOVERY_KEY - OFFSET_BODY)
        labelBytes(label).copyInto(body, ADOPT_LABEL - OFFSET_BODY)
        entropy.copyInto(body, ADOPT_ENTROPY - OFFSET_BODY)

        // An AdoptRoot is only ever the first record of a chain, so its prevHash is the 32 zero bytes and
        // its seq is 1. Neither is a parameter: a caller that could pass something else could build a
        // record the server can only refuse.
        return envelope(
            type = AuthorityRecordType.ADOPT_ROOT,
            accountReference = accountReference,
            prevHash = AuthorityRecord.genesisPrevHash(),
            seq = 1,
            body = body,
        )
    }

    /**
     * The canonical bytes of a `DeviceGrant` (`GUAD`, 161 bytes).
     *
     * @param accountReference the 34 raw accountId bytes, as [adoptRoot] takes them.
     * @param prevHash the 32 bytes of the head this record appends to.
     * @param seq exactly one more than the head's.
     * @param granteeDeviceKey the NEW device's own authority public key. It generated it itself and never
     * received one: a copied key makes revocation meaningless, because the revoked device still holds the
     * key the account is defined by (ADM-009 decision 5).
     * @param label at most 16 bytes of UTF-8, naming the device being granted.
     * @param authorizingKey the granting device's own authority key, inside the hashed bytes, so a later log
     * leaf commits who authorized this and not only that someone did (decision 2). The server checks it
     * equals the verifying key rather than inferring it.
     */
    fun deviceGrant(
        accountReference: ByteArray,
        prevHash: ByteArray,
        seq: Long,
        granteeDeviceKey: ByteArray,
        label: String,
        authorizingKey: ByteArray,
    ): ByteArray {
        requireKey(granteeDeviceKey, "device_key")
        requireKey(authorizingKey, "authorizing_key")
        if (MessageDigest.isEqual(granteeDeviceKey, authorizingKey)) {
            // Not a rule the server's decoder states, because there it cannot be told from a legitimate
            // re-grant. Here it can only be this client having handed the grantee its own key, which is the
            // key-copying decision 5 rejects, so it is refused before it is signed.
            throw InvalidAuthorityRecordException(
                "duplicate_keys",
                "a device cannot grant authority to its own key",
            )
        }

        val body = ByteArray(AuthorityRecordType.DEVICE_GRANT.bodyLength)
        granteeDeviceKey.copyInto(body, GRANT_DEVICE_KEY - OFFSET_BODY)
        body[GRANT_FLAGS - OFFSET_BODY] = AuthorityRecord.FLAGS_NONE.toByte()
        labelBytes(label).copyInto(body, GRANT_LABEL - OFFSET_BODY)
        authorizingKey.copyInto(body, GRANT_AUTHORIZING_KEY - OFFSET_BODY)

        return envelope(
            type = AuthorityRecordType.DEVICE_GRANT,
            accountReference = accountReference,
            prevHash = prevHash,
            seq = seq,
            body = body,
        )
    }

    /** SHA-256 over canonical bytes, which is a record's own hash. */
    fun hash(canonicalBytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(canonicalBytes)

    /**
     * The 32 prevHash bytes from the `headHash` hex `GET /account/authority` reports, which is 64 zeros
     * while the chain is empty.
     */
    fun prevHashFromHex(headHashHex: String): ByteArray {
        val hex = headHashHex.trim()
        if (hex.length != AuthorityRecord.HASH_LENGTH * 2 || hex.any { it.digitToIntOrNull(16) == null }) {
            throw InvalidAuthorityRecordException("bad_prev_hash", "a chain head hash is 64 hex characters")
        }
        return ByteArray(AuthorityRecord.HASH_LENGTH) { index ->
            (hex[index * 2].digitToInt(16) shl 4 or hex[index * 2 + 1].digitToInt(16)).toByte()
        }
    }

    /**
     * 16 label bytes, UTF-8, zero-padded to the end.
     *
     * A label longer than the field is refused rather than truncated: truncating can cut a multi-byte
     * character in half, and the notification that names it would then name something the user never typed.
     */
    fun labelBytes(label: String): ByteArray {
        val utf8 = label.toByteArray(Charsets.UTF_8)
        if (utf8.isEmpty()) {
            throw InvalidAuthorityRecordException("empty_label", "a record carries a label a notification can name")
        }
        if (utf8.size > AuthorityRecord.LABEL_LENGTH) {
            throw InvalidAuthorityRecordException(
                "label_too_long",
                "a label is at most ${AuthorityRecord.LABEL_LENGTH} bytes of UTF-8",
            )
        }
        if (utf8.contains(0)) {
            // The padding rule is "nothing non-zero after the first zero", so an embedded zero would make
            // the encoding non-canonical and the server would refuse it as non_canonical_label.
            throw InvalidAuthorityRecordException("non_canonical_label", "a label holds no zero byte")
        }
        return utf8.copyOf(AuthorityRecord.LABEL_LENGTH)
    }

    fun randomEntropy(): ByteArray = ByteArray(AuthorityRecord.ENTROPY_LENGTH).also(random::nextBytes)

    private fun envelope(
        type: AuthorityRecordType,
        accountReference: ByteArray,
        prevHash: ByteArray,
        seq: Long,
        body: ByteArray,
    ): ByteArray {
        requireLength(accountReference, AuthorityRecord.ACCOUNT_REFERENCE_LENGTH, "account_reference")
        requireLength(prevHash, AuthorityRecord.HASH_LENGTH, "prev_hash")
        if (seq < 1) {
            throw InvalidAuthorityRecordException("bad_seq", "seq starts at 1")
        }

        val out = ByteArray(type.length)
        type.magicBytes.copyInto(out, 0)
        out[OFFSET_VERSION] = AuthorityRecord.VERSION.toByte()
        out[OFFSET_SUITE] = AuthorityRecord.SUITE_ED25519_SHA256.toByte()
        accountReference.copyInto(out, OFFSET_ACCOUNT)
        prevHash.copyInto(out, OFFSET_PREV_HASH)
        // Unsigned big-endian, no delimiters, so the bytes hashed here are the bytes the server hashes.
        for (index in 0 until 8) {
            out[OFFSET_SEQ + index] = (seq ushr 8 * (7 - index)).toByte()
        }
        body.copyInto(out, OFFSET_BODY)
        return out
    }

    private fun requireKey(raw: ByteArray, field: String) {
        requireLength(raw, AuthorityRecord.KEY_LENGTH, field)
        if (Ed25519PublicKeys.isAllZero(raw)) {
            throw InvalidAuthorityRecordException("zero_$field", "$field is all zero")
        }
        if (!Ed25519PublicKeys.isOnCurve(raw)) {
            throw InvalidAuthorityRecordException("invalid_$field", "$field does not decode to a curve point")
        }
    }

    private fun requireDistinct(deviceKey: ByteArray, recoveryKey: ByteArray) {
        if (MessageDigest.isEqual(deviceKey, recoveryKey)) {
            throw InvalidAuthorityRecordException(
                "duplicate_keys",
                "the recovery authority key must differ from the device key",
            )
        }
    }

    private fun requireLength(value: ByteArray, length: Int, field: String) {
        if (value.size != length) {
            throw InvalidAuthorityRecordException("wrong_length", "$field is $length bytes")
        }
    }
}
