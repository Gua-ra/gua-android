/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

/**
 * GUA FORK: the shared envelope of the account authority chain (ADM-009 decision 2), and the five record
 * types that ride in it. Port of the identity-service `AuthorityRecord` and `AuthorityRecordType`.
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
 *
 * The chain is the authority: there is no ambient "the account's key" outside it, which is why nothing here
 * has a mutable field and why the client keeps the bytes it signed rather than a parsed view of them.
 */
object AuthorityRecord {
    const val MAGIC_LENGTH = 4
    const val VERSION = 0x01

    /** Ed25519 with SHA-256. The one suite ADM-009 defines. */
    const val SUITE_ED25519_SHA256 = 0x01

    /** The 34 raw accountId bytes, as [io.element.android.libraries.guaresolver.genesis.AccountId.rawBytes] returns them. */
    const val ACCOUNT_REFERENCE_LENGTH = 34
    const val HASH_LENGTH = 32
    const val KEY_LENGTH = 32
    const val LABEL_LENGTH = 16
    const val ENTROPY_LENGTH = 16

    /** The server challenge every record signs (ADM-009 decision 2, the one preimage rule). */
    const val CHALLENGE_LENGTH = 32
    const val SIGNATURE_LENGTH = 64
    const val ENVELOPE_LENGTH = 80

    /** One committed recovery authority key, framework 0x01 (ADM-008 decision 4). */
    const val RECOVERY_FRAMEWORK_COMMITTED_KEY = 0x01

    /** No grant flag is defined. A decoder refuses a reserved bit rather than ignoring it. */
    const val FLAGS_NONE = 0x00

    const val REASON_UNSPECIFIED = 0x01
    const val REASON_LOST = 0x02
    const val REASON_REPLACED = 0x03
    const val REASON_COMPROMISED = 0x04

    /** Signed by the committed recovery authority key: rank 2, which no pending record can block. */
    const val AUTHORIZATION_RECOVERY_KEY = 0x01

    /** Authorized through a completed account recovery: rank 0, and vetoable by any active device. */
    const val AUTHORIZATION_ACCOUNT_RECOVERY = 0x02

    /** The reasons a `DeviceRevoke` may carry. A decoder refuses anything else rather than storing it. */
    val REVOCATION_REASONS: Set<Int> = setOf(REASON_UNSPECIFIED, REASON_LOST, REASON_REPLACED, REASON_COMPROMISED)

    /** The first record's prevHash. */
    fun genesisPrevHash(): ByteArray = ByteArray(HASH_LENGTH)
}

/**
 * The five record types, their magic (which is also their signature domain) and their total canonical length.
 *
 * The magic table is what makes "no record can be replayed as another type" checkable in one place: a client
 * that knew only the magics it writes could not tell a wrong one from an unknown one.
 */
enum class AuthorityRecordType(val magic: String, val length: Int) {
    /** deviceKey 32 | recoveryFrameworkId 1 | recoveryAuthorityKey 32 | label 16 | entropy 16. */
    ADOPT_ROOT("GUAA", 177),

    /** deviceKey 32 | flags 1 | label 16 | authorizingKey 32. */
    DEVICE_GRANT("GUAD", 161),

    /** deviceKey 32 | reason 1 | authorizingKey 32. */
    DEVICE_REVOKE("GUAX", 145),

    /** deviceKey 32 | recoveryAuthorityKey 32 | label 16 | entropy 16 | authorization 1 | authorizingKey 32. */
    AUTHORITY_RECOVERY("GUAR", 209),

    /**
     * opposedRecordHash 32 | authorizingKey 32.
     *
     * It takes no slot and starts no window: it cancels the record it names, or it is refused. Revision 4 of
     * ADM-009 adds it because revisions 1 to 3 told an active device to object and gave it nothing to sign,
     * and the server was right to refuse that claim from a bearer session alone.
     */
    OPPOSE("GUAO", 144);

    val magicBytes: ByteArray get() = magic.toByteArray(Charsets.US_ASCII)

    val bodyLength: Int get() = length - AuthorityRecord.ENVELOPE_LENGTH

    companion object {
        /** The type whose magic these four ASCII bytes are, or null when no type claims them. */
        fun ofMagic(magic: String): AuthorityRecordType? = entries.firstOrNull { it.magic == magic }
    }
}

/**
 * A value that is not a well-formed authority record (ADM-009 decision 2).
 *
 * [reason] is the machine-readable rule that refused it, in the same vocabulary the server's decoder
 * returns in `invalid_authority_record`, so a refusal that crosses the wire and one raised here can be
 * compared directly instead of guessed at.
 */
class InvalidAuthorityRecordException(
    val reason: String,
    val detail: String,
) : Exception("$reason: $detail")
