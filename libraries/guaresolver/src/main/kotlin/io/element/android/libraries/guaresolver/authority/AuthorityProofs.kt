/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

object AuthorityProofs {
    const val APPROVAL_DOMAIN = "gua-authority-approval.v1"

    const val NOTIFICATION_DOMAIN = "gua-authority-notification.v1"

    const val APPROVAL_ID_LENGTH = 16

    private val approvalDomainBytes = APPROVAL_DOMAIN.toByteArray(Charsets.US_ASCII)

    private val notificationDomainBytes = NOTIFICATION_DOMAIN.toByteArray(Charsets.US_ASCII)

    const val APPROVAL_PREIMAGE_LENGTH = 139

    const val NOTIFICATION_PREIMAGE_LENGTH = 159

    /** magic || server challenge (32) || canonical record bytes. */
    fun recordPreimage(type: AuthorityRecordType, challenge: ByteArray, canonicalBytes: ByteArray): ByteArray {
        if (challenge.size != AuthorityRecord.CHALLENGE_LENGTH) {
            throw InvalidAuthorityRecordException(
                "wrong_length",
                "an authority challenge is ${AuthorityRecord.CHALLENGE_LENGTH} bytes",
            )
        }
        if (canonicalBytes.size != type.length) {
            throw InvalidAuthorityRecordException("wrong_length", "$type is ${type.length} bytes")
        }
        return type.magicBytes + challenge + canonicalBytes
    }

    /** domain || accountId (34) || approval id (16) || action digest (32) || challenge (32). */
    fun approvalPreimage(
        accountReference: ByteArray,
        approvalId: ByteArray,
        actionDigest: ByteArray,
        challenge: ByteArray,
    ): ByteArray {
        require(accountReference, AuthorityRecord.ACCOUNT_REFERENCE_LENGTH, "account_reference")
        require(approvalId, APPROVAL_ID_LENGTH, "approval_id")
        require(actionDigest, AuthorityRecord.HASH_LENGTH, "action_digest")
        require(challenge, AuthorityRecord.CHALLENGE_LENGTH, "challenge")
        return approvalDomainBytes + accountReference + approvalId + actionDigest + challenge
    }

    /** domain || accountId (34) || SHA-256 of the installation id (32) || device key (32) || challenge (32). */
    fun notificationPreimage(
        accountReference: ByteArray,
        installationIdHash: ByteArray,
        deviceKey: ByteArray,
        challenge: ByteArray,
    ): ByteArray {
        require(accountReference, AuthorityRecord.ACCOUNT_REFERENCE_LENGTH, "account_reference")
        require(installationIdHash, AuthorityRecord.HASH_LENGTH, "installation_id_hash")
        require(deviceKey, AuthorityRecord.KEY_LENGTH, "device_key")
        require(challenge, AuthorityRecord.CHALLENGE_LENGTH, "challenge")
        return notificationDomainBytes + accountReference + installationIdHash + deviceKey + challenge
    }

    private fun require(value: ByteArray, length: Int, field: String) {
        if (value.size != length) {
            throw InvalidAuthorityRecordException("wrong_length", "$field is $length bytes")
        }
    }
}
