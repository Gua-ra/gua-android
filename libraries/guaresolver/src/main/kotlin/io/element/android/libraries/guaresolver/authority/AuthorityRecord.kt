/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

/**
 * Canonical fixed-width encoding. Signatures cover these exact bytes.
 *
 * ```
 * off len field
 * 0   4   magic, ASCII, one per record type, and the signature domain
 * 4   1   version = 0x01
 * 5   1   suite = 0x01, Ed25519 with SHA-256
 * 6   34  the accountId raw bytes, exactly what AccountId.rawBytes() returns
 * 40  32  prevHash, SHA-256 over the previous record's canonical bytes, 32 zeros in the first
 * 72  8   seq, unsigned big-endian, 1 in the first record and one more than the previous
 * 80  ..  body, fixed per type
 * ```
 */
object AuthorityRecord {
    const val MAGIC_LENGTH = 4
    const val VERSION = 0x01

    const val SUITE_ED25519_SHA256 = 0x01

    const val ACCOUNT_REFERENCE_LENGTH = 34
    const val HASH_LENGTH = 32
    const val KEY_LENGTH = 32
    const val LABEL_LENGTH = 16
    const val ENTROPY_LENGTH = 16

    const val CHALLENGE_LENGTH = 32
    const val SIGNATURE_LENGTH = 64
    const val ENVELOPE_LENGTH = 80

    const val RECOVERY_FRAMEWORK_COMMITTED_KEY = 0x01

    const val FLAGS_NONE = 0x00

    const val REASON_UNSPECIFIED = 0x01
    const val REASON_LOST = 0x02
    const val REASON_REPLACED = 0x03
    const val REASON_COMPROMISED = 0x04

    const val AUTHORIZATION_RECOVERY_KEY = 0x01

    const val AUTHORIZATION_ACCOUNT_RECOVERY = 0x02

    val REVOCATION_REASONS: Set<Int> = setOf(REASON_UNSPECIFIED, REASON_LOST, REASON_REPLACED, REASON_COMPROMISED)

    fun genesisPrevHash(): ByteArray = ByteArray(HASH_LENGTH)
}

enum class AuthorityRecordType(val magic: String, val length: Int) {
    /** deviceKey 32 | recoveryFrameworkId 1 | recoveryAuthorityKey 32 | label 16 | entropy 16. */
    ADOPT_ROOT("GUAA", 177),

    /** deviceKey 32 | flags 1 | label 16 | authorizingKey 32. */
    DEVICE_GRANT("GUAD", 161),

    /** deviceKey 32 | reason 1 | authorizingKey 32. */
    DEVICE_REVOKE("GUAX", 145),

    /** deviceKey 32 | recoveryAuthorityKey 32 | label 16 | entropy 16 | authorization 1 | authorizingKey 32. */
    AUTHORITY_RECOVERY("GUAR", 209),

    /** opposedRecordHash 32 | authorizingKey 32. */
    OPPOSE("GUAO", 144);

    val magicBytes: ByteArray get() = magic.toByteArray(Charsets.US_ASCII)

    val bodyLength: Int get() = length - AuthorityRecord.ENVELOPE_LENGTH

    companion object {
        fun ofMagic(magic: String): AuthorityRecordType? = entries.firstOrNull { it.magic == magic }
    }
}

class InvalidAuthorityRecordException(
    val reason: String,
    val detail: String,
) : Exception("$reason: $detail")
