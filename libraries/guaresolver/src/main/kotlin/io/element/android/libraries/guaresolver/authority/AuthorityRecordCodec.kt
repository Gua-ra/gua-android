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
 * Every record a phone can sign is built here, because every one of them now has a screen behind it:
 * `AdoptRoot`, `DeviceGrant`, `DeviceRevoke`, `AuthorityRecovery` and `Oppose`.
 *
 * [parse] is the same rules read backwards, with the refusal tokens the server's own decoder uses. It is what
 * the golden vectors of `authority-vectors.v1.json` are checked against, and it is run over the bytes this
 * client just built before a challenge is spent on them.
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

    // DeviceRevoke body.
    private const val REVOKE_DEVICE_KEY = OFFSET_BODY
    private const val REVOKE_REASON = 112
    private const val REVOKE_AUTHORIZING_KEY = 113

    // AuthorityRecovery body.
    private const val RECOVER_DEVICE_KEY = OFFSET_BODY
    private const val RECOVER_RECOVERY_KEY = 112
    private const val RECOVER_LABEL = 144
    private const val RECOVER_ENTROPY = 160
    private const val RECOVER_AUTHORIZATION = 176
    private const val RECOVER_AUTHORIZING_KEY = 177

    // Oppose body.
    private const val OPPOSE_RECORD_HASH = OFFSET_BODY
    private const val OPPOSE_AUTHORIZING_KEY = 112

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


    /**
     * The canonical bytes of a `DeviceRevoke` (`GUAX`, 145 bytes).
     *
     * Revoking ANOTHER device waits out the window and is notified, and any active device other than the one
     * named may object. Revoking ITSELF takes effect at once, because a device removing its own authority
     * reduces what an attacker holding it could do. The difference is not in these bytes: the server reads it
     * from whether the named key is the signing key, so a client cannot ask for the immediate path by
     * labelling a record differently.
     *
     * @param deviceKey the key being removed.
     * @param reason one of [AuthorityRecord.REVOCATION_REASONS]. It is not a free-text field, so nothing the
     * owner typed can end up in a notification.
     * @param authorizingKey the signing device's own authority key.
     */
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

    /**
     * The canonical bytes of an `AuthorityRecovery` (`GUAR`, 209 bytes), which replaces the whole device set
     * and the recovery authority key in one record.
     *
     * @param authorization [AuthorityRecord.AUTHORIZATION_RECOVERY_KEY] when the record is signed by the
     * recovery authority key the account committed, which is rank 2 and the one record an intruder holding
     * every device cannot cancel, or [AuthorityRecord.AUTHORIZATION_ACCOUNT_RECOVERY] for the weaker path,
     * which is rank 0 and any active device may veto immediately.
     * @param authorizingKey the recovery authority public key under authorization 0x01, and null under 0x02,
     * where the field is 32 zero bytes. The pairing is enforced in both directions here and again by the
     * server's decoder: a record that named a key under the account-recovery path would be claiming an
     * authorization it does not have.
     */
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

    /**
     * The canonical bytes of an `Oppose` (`GUAO`, 144 bytes).
     *
     * @param prevHash the prevHash of the record being opposed, which is the chain head the pending record was
     * accepted against: a pending record holds its seq without being appended, so the head the client reads is
     * still the one it names.
     * @param seq the pending record's own seq. An Oppose takes no position of its own, so it carries the
     * position of what it cancels and the server refuses it as stale when either half does not match.
     * @param opposedRecordHash the 32 bytes of the pending record's hash.
     */
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

    /**
     * Reads canonical bytes back, enforcing every rule ADM-009 decision 2 gives a decoder, and refusing with
     * the same token the server's `invalid_authority_record` names.
     *
     * The order of the checks is part of the contract, not an implementation detail: a record with two faults
     * has to be refused for the same one on both sides, or the golden vectors could not name a single reason
     * per entry.
     */
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

    /** The one pairing of an authorization byte and an authorizing key, checked in both directions. */
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

    /** "Nothing non-zero after the first zero", which is what makes a 16-byte label one encoding only. */
    private fun requireCanonicalLabel(canonicalBytes: ByteArray, offset: Int) {
        val label = canonicalBytes.copyOfRange(offset, offset + AuthorityRecord.LABEL_LENGTH)
        val firstZero = label.indexOfFirst { it == 0.toByte() }
        if (firstZero >= 0 && label.drop(firstZero + 1).any { it != 0.toByte() }) {
            throw InvalidAuthorityRecordException("non_canonical_label", "a label is zero-padded to the end")
        }
    }
}

/**
 * One record read back out of its canonical bytes.
 *
 * Deliberately not a mutable view and deliberately not re-encoded: what a caller does with this is decide
 * whether to submit the bytes it already holds, and the bytes the server hashes are the bytes it received.
 */
class ParsedAuthorityRecord(
    val type: AuthorityRecordType,
    val accountReference: ByteArray,
    val prevHash: ByteArray,
    val seq: Long,
    /** The device key the record names, which every type but `Oppose` carries. */
    val deviceKey: ByteArray?,
    val recoveryAuthorityKey: ByteArray?,
    /** Null on an `AdoptRoot`, whose signer is the device key it commits, and under authorization 0x02. */
    val authorizingKey: ByteArray?,
    val opposedRecordHash: ByteArray? = null,
)
