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

object AuthorityRecordCodec {
    private val random = SecureRandom()

    private const val OFFSET_VERSION = 4
    private const val OFFSET_SUITE = 5
    private const val OFFSET_ACCOUNT = 6
    private const val OFFSET_PREV_HASH = 40
    private const val OFFSET_SEQ = 72
    private const val OFFSET_BODY = 80

    private const val ADOPT_DEVICE_KEY = OFFSET_BODY
    private const val ADOPT_FRAMEWORK = 112
    private const val ADOPT_RECOVERY_KEY = 113
    private const val ADOPT_LABEL = 145
    private const val ADOPT_ENTROPY = 161

    private const val GRANT_DEVICE_KEY = OFFSET_BODY
    private const val GRANT_FLAGS = 112
    private const val GRANT_LABEL = 113
    private const val GRANT_AUTHORIZING_KEY = 129

    private const val REVOKE_DEVICE_KEY = OFFSET_BODY
    private const val REVOKE_REASON = 112
    private const val REVOKE_AUTHORIZING_KEY = 113

    private const val RECOVER_DEVICE_KEY = OFFSET_BODY
    private const val RECOVER_RECOVERY_KEY = 112
    private const val RECOVER_LABEL = 144
    private const val RECOVER_ENTROPY = 160
    private const val RECOVER_AUTHORIZATION = 176
    private const val RECOVER_AUTHORIZING_KEY = 177

    private const val OPPOSE_RECORD_HASH = OFFSET_BODY
    private const val OPPOSE_AUTHORIZING_KEY = 112

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

        return envelope(
            type = AuthorityRecordType.ADOPT_ROOT,
            accountReference = accountReference,
            prevHash = AuthorityRecord.genesisPrevHash(),
            seq = 1,
            body = body,
        )
    }

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
            // Client-only rule: the server cannot tell this from a legitimate re-grant.
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

    fun deviceRevoke(
        accountReference: ByteArray,
        prevHash: ByteArray,
        seq: Long,
        deviceKey: ByteArray,
        reason: Int,
        authorizingKey: ByteArray,
    ): ByteArray {
        requireKey(deviceKey, "device_key")
        requireKey(authorizingKey, "authorizing_key")
        if (reason !in AuthorityRecord.REVOCATION_REASONS) {
            throw InvalidAuthorityRecordException("unknown_revocation_reason", "reason $reason is not defined")
        }

        val body = ByteArray(AuthorityRecordType.DEVICE_REVOKE.bodyLength)
        deviceKey.copyInto(body, REVOKE_DEVICE_KEY - OFFSET_BODY)
        body[REVOKE_REASON - OFFSET_BODY] = reason.toByte()
        authorizingKey.copyInto(body, REVOKE_AUTHORIZING_KEY - OFFSET_BODY)

        return envelope(AuthorityRecordType.DEVICE_REVOKE, accountReference, prevHash, seq, body)
    }

    fun authorityRecovery(
        accountReference: ByteArray,
        prevHash: ByteArray,
        seq: Long,
        deviceKey: ByteArray,
        recoveryAuthorityKey: ByteArray,
        label: String,
        authorization: Int,
        authorizingKey: ByteArray?,
        entropy: ByteArray = randomEntropy(),
    ): ByteArray {
        requireKey(deviceKey, "device_key")
        requireKey(recoveryAuthorityKey, "recovery_key")
        requireDistinct(deviceKey, recoveryAuthorityKey)
        requireLength(entropy, AuthorityRecord.ENTROPY_LENGTH, "entropy")
        val authorizing = requireAuthorizationPairing(authorization, authorizingKey)

        val body = ByteArray(AuthorityRecordType.AUTHORITY_RECOVERY.bodyLength)
        deviceKey.copyInto(body, RECOVER_DEVICE_KEY - OFFSET_BODY)
        recoveryAuthorityKey.copyInto(body, RECOVER_RECOVERY_KEY - OFFSET_BODY)
        labelBytes(label).copyInto(body, RECOVER_LABEL - OFFSET_BODY)
        entropy.copyInto(body, RECOVER_ENTROPY - OFFSET_BODY)
        body[RECOVER_AUTHORIZATION - OFFSET_BODY] = authorization.toByte()
        authorizing.copyInto(body, RECOVER_AUTHORIZING_KEY - OFFSET_BODY)

        return envelope(AuthorityRecordType.AUTHORITY_RECOVERY, accountReference, prevHash, seq, body)
    }

    fun oppose(
        accountReference: ByteArray,
        prevHash: ByteArray,
        seq: Long,
        opposedRecordHash: ByteArray,
        authorizingKey: ByteArray,
    ): ByteArray {
        requireLength(opposedRecordHash, AuthorityRecord.HASH_LENGTH, "opposed_record")
        if (opposedRecordHash.all { it == 0.toByte() }) {
            throw InvalidAuthorityRecordException("zero_opposed_record", "an objection names the record it cancels")
        }
        requireKey(authorizingKey, "authorizing_key")

        val body = ByteArray(AuthorityRecordType.OPPOSE.bodyLength)
        opposedRecordHash.copyInto(body, OPPOSE_RECORD_HASH - OFFSET_BODY)
        authorizingKey.copyInto(body, OPPOSE_AUTHORIZING_KEY - OFFSET_BODY)

        return envelope(AuthorityRecordType.OPPOSE, accountReference, prevHash, seq, body)
    }

    /** The check order is part of the contract: a record with two faults is refused for the same one as on the server. */
    fun parse(canonicalBytes: ByteArray): ParsedAuthorityRecord {
        if (canonicalBytes.size < AuthorityRecord.MAGIC_LENGTH) {
            throw InvalidAuthorityRecordException("wrong_length", "a record carries at least its magic")
        }
        val magic = String(canonicalBytes, 0, AuthorityRecord.MAGIC_LENGTH, Charsets.US_ASCII)
        val type = AuthorityRecordType.ofMagic(magic)
            ?: throw InvalidAuthorityRecordException("bad_magic", "no record type has that magic")
        if (canonicalBytes.size != type.length) {
            throw InvalidAuthorityRecordException("wrong_length", "$type is ${type.length} bytes")
        }
        if (canonicalBytes[OFFSET_VERSION].toInt() != AuthorityRecord.VERSION) {
            throw InvalidAuthorityRecordException("unknown_version", "this build writes version 1 only")
        }
        if (canonicalBytes[OFFSET_SUITE].toInt() != AuthorityRecord.SUITE_ED25519_SHA256) {
            throw InvalidAuthorityRecordException("unknown_suite", "this build knows suite 1 only")
        }
        var seq = 0L
        for (index in 0 until 8) {
            seq = seq shl 8 or (canonicalBytes[OFFSET_SEQ + index].toLong() and 0xFF)
        }
        if (seq < 1) {
            throw InvalidAuthorityRecordException("bad_seq", "seq starts at 1")
        }

        val accountReference = canonicalBytes.copyOfRange(OFFSET_ACCOUNT, OFFSET_PREV_HASH)
        val prevHash = canonicalBytes.copyOfRange(OFFSET_PREV_HASH, OFFSET_SEQ)
        return when (type) {
            AuthorityRecordType.ADOPT_ROOT -> {
                val deviceKey = keyAt(canonicalBytes, ADOPT_DEVICE_KEY, "device_key")
                if (canonicalBytes[ADOPT_FRAMEWORK].toInt() != AuthorityRecord.RECOVERY_FRAMEWORK_COMMITTED_KEY) {
                    throw InvalidAuthorityRecordException(
                        "unknown_recovery_framework",
                        "framework 0x01 is the only one defined",
                    )
                }
                val recoveryKey = keyAt(canonicalBytes, ADOPT_RECOVERY_KEY, "recovery_key")
                requireDistinct(deviceKey, recoveryKey)
                requireCanonicalLabel(canonicalBytes, ADOPT_LABEL)
                ParsedAuthorityRecord(type, accountReference, prevHash, seq, deviceKey, recoveryKey, null)
            }
            AuthorityRecordType.DEVICE_GRANT -> {
                val deviceKey = keyAt(canonicalBytes, GRANT_DEVICE_KEY, "device_key")
                if (canonicalBytes[GRANT_FLAGS].toInt() != AuthorityRecord.FLAGS_NONE) {
                    throw InvalidAuthorityRecordException("unknown_flags", "no grant flag is defined")
                }
                requireCanonicalLabel(canonicalBytes, GRANT_LABEL)
                val authorizingKey = keyAt(canonicalBytes, GRANT_AUTHORIZING_KEY, "authorizing_key")
                ParsedAuthorityRecord(type, accountReference, prevHash, seq, deviceKey, null, authorizingKey)
            }
            AuthorityRecordType.DEVICE_REVOKE -> {
                val deviceKey = keyAt(canonicalBytes, REVOKE_DEVICE_KEY, "device_key")
                if (canonicalBytes[REVOKE_REASON].toInt() !in AuthorityRecord.REVOCATION_REASONS) {
                    throw InvalidAuthorityRecordException("unknown_revocation_reason", "that reason is not defined")
                }
                val authorizingKey = keyAt(canonicalBytes, REVOKE_AUTHORIZING_KEY, "authorizing_key")
                ParsedAuthorityRecord(type, accountReference, prevHash, seq, deviceKey, null, authorizingKey)
            }
            AuthorityRecordType.AUTHORITY_RECOVERY -> {
                val deviceKey = keyAt(canonicalBytes, RECOVER_DEVICE_KEY, "device_key")
                val recoveryKey = keyAt(canonicalBytes, RECOVER_RECOVERY_KEY, "recovery_key")
                requireDistinct(deviceKey, recoveryKey)
                requireCanonicalLabel(canonicalBytes, RECOVER_LABEL)
                val authorization = canonicalBytes[RECOVER_AUTHORIZATION].toInt()
                val raw = canonicalBytes.copyOfRange(
                    RECOVER_AUTHORIZING_KEY,
                    RECOVER_AUTHORIZING_KEY + AuthorityRecord.KEY_LENGTH,
                )
                val authorizingKey = when (authorization) {
                    AuthorityRecord.AUTHORIZATION_RECOVERY_KEY -> {
                        if (Ed25519PublicKeys.isAllZero(raw)) {
                            throw InvalidAuthorityRecordException(
                                "authorizing_key_required",
                                "the recovery-key path names the key that signs it",
                            )
                        }
                        keyAt(canonicalBytes, RECOVER_AUTHORIZING_KEY, "authorizing_key")
                    }
                    AuthorityRecord.AUTHORIZATION_ACCOUNT_RECOVERY -> {
                        if (!Ed25519PublicKeys.isAllZero(raw)) {
                            throw InvalidAuthorityRecordException(
                                "authorizing_key_not_permitted",
                                "the account-recovery path names no key",
                            )
                        }
                        null
                    }
                    else -> throw InvalidAuthorityRecordException(
                        "unknown_authorization",
                        "authorization $authorization is not defined",
                    )
                }
                ParsedAuthorityRecord(type, accountReference, prevHash, seq, deviceKey, recoveryKey, authorizingKey)
            }
            AuthorityRecordType.OPPOSE -> {
                val opposed = canonicalBytes.copyOfRange(
                    OPPOSE_RECORD_HASH,
                    OPPOSE_RECORD_HASH + AuthorityRecord.HASH_LENGTH,
                )
                if (opposed.all { it == 0.toByte() }) {
                    throw InvalidAuthorityRecordException(
                        "zero_opposed_record",
                        "an objection names the record it cancels",
                    )
                }
                val authorizingKey = keyAt(canonicalBytes, OPPOSE_AUTHORIZING_KEY, "authorizing_key")
                ParsedAuthorityRecord(
                    type = type,
                    accountReference = accountReference,
                    prevHash = prevHash,
                    seq = seq,
                    deviceKey = null,
                    recoveryAuthorityKey = null,
                    authorizingKey = authorizingKey,
                    opposedRecordHash = opposed,
                )
            }
        }
    }

    fun hash(canonicalBytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(canonicalBytes)

    fun prevHashFromHex(headHashHex: String): ByteArray {
        val hex = headHashHex.trim()
        if (hex.length != AuthorityRecord.HASH_LENGTH * 2 || hex.any { it.digitToIntOrNull(16) == null }) {
            throw InvalidAuthorityRecordException("bad_prev_hash", "a chain head hash is 64 hex characters")
        }
        return ByteArray(AuthorityRecord.HASH_LENGTH) { index ->
            (hex[index * 2].digitToInt(16) shl 4 or hex[index * 2 + 1].digitToInt(16)).toByte()
        }
    }

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
        for (index in 0 until 8) {
            out[OFFSET_SEQ + index] = (seq ushr 8 * (7 - index)).toByte()
        }
        body.copyInto(out, OFFSET_BODY)
        return out
    }

    private fun requireKey(raw: ByteArray, field: String) {
        requireLength(raw, AuthorityRecord.KEY_LENGTH, field)
        // Checked separately: the all-zero encoding decodes to a valid low-order point.
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

    private fun requireAuthorizationPairing(authorization: Int, authorizingKey: ByteArray?): ByteArray =
        when (authorization) {
            AuthorityRecord.AUTHORIZATION_RECOVERY_KEY -> {
                val key = authorizingKey
                    ?: throw InvalidAuthorityRecordException(
                        "authorizing_key_required",
                        "the recovery-key path names the key that signs it",
                    )
                requireKey(key, "authorizing_key")
                key
            }
            AuthorityRecord.AUTHORIZATION_ACCOUNT_RECOVERY -> {
                if (authorizingKey != null) {
                    throw InvalidAuthorityRecordException(
                        "authorizing_key_not_permitted",
                        "the account-recovery path names no key",
                    )
                }
                ByteArray(AuthorityRecord.KEY_LENGTH)
            }
            else -> throw InvalidAuthorityRecordException(
                "unknown_authorization",
                "authorization $authorization is not defined",
            )
        }

    private fun keyAt(canonicalBytes: ByteArray, offset: Int, field: String): ByteArray {
        val raw = canonicalBytes.copyOfRange(offset, offset + AuthorityRecord.KEY_LENGTH)
        requireKey(raw, field)
        return raw
    }

    private fun requireCanonicalLabel(canonicalBytes: ByteArray, offset: Int) {
        val label = canonicalBytes.copyOfRange(offset, offset + AuthorityRecord.LABEL_LENGTH)
        val firstZero = label.indexOfFirst { it == 0.toByte() }
        if (firstZero >= 0 && label.drop(firstZero + 1).any { it != 0.toByte() }) {
            throw InvalidAuthorityRecordException("non_canonical_label", "a label is zero-padded to the end")
        }
    }
}

data class ParsedAuthorityRecord(
    val type: AuthorityRecordType,
    val accountReference: ByteArray,
    val prevHash: ByteArray,
    val seq: Long,
    val deviceKey: ByteArray?,
    val recoveryAuthorityKey: ByteArray?,
    val authorizingKey: ByteArray?,
    val opposedRecordHash: ByteArray? = null,
)
