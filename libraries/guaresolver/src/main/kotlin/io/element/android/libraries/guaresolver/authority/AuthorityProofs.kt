/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

/**
 * GUA FORK: the one preimage rule of the authority chain, and the browser-approval preimage beside it
 * (ADM-009 decisions 2 and 6). Kotlin side of the identity-service `AuthorityProofs`; this side builds the
 * preimages and signs them, the server side verifies them.
 *
 * **One preimage, for every type.**
 *
 * ```
 * magic || the 32 bytes of the server challenge minted for that transition || the canonical bytes
 * ```
 *
 * The magic is the signature domain, so no record can be replayed as another type; the accountId is inside
 * the canonical bytes, so none can be replayed into another account; and the challenge is inside every
 * signature, so no record is precomputable on other hardware, transferable to another party, or
 * resubmittable after it was opposed. One builder used by every type is what keeps that rule from going
 * missing per type, which is the defect revision 3 of ADM-009 exists to fix.
 *
 * The record's magic stands in for a separate ASCII domain string, exactly as on the server: each type has
 * its own magic already, so a second constant would be a second thing to keep in step for nothing.
 */
object AuthorityProofs {
    /** The domain a device signs when it approves an action a browser session started (decision 6). */
    const val APPROVAL_DOMAIN = "gua-authority-approval.v1"

    /**
     * The domain an install signs to bind its security-notification registration to a device key.
     *
     * ADM-009 does not define this binding: gate 2's removal tiers need it, and identity-service states it in
     * its own `AuthorityProofs` so both clients sign the same bytes. It belongs in a revision of the record
     * rather than only in three implementations, and that is noted rather than left for someone to discover.
     */
    const val NOTIFICATION_DOMAIN = "gua-authority-notification.v1"

    /** Bytes of a pending-approval id inside the preimage. */
    const val APPROVAL_ID_LENGTH = 16

    private val approvalDomainBytes = APPROVAL_DOMAIN.toByteArray(Charsets.US_ASCII)

    private val notificationDomainBytes = NOTIFICATION_DOMAIN.toByteArray(Charsets.US_ASCII)

    /** 25 + 34 + 16 + 32 + 32. */
    const val APPROVAL_PREIMAGE_LENGTH = 139

    /** 29 + 34 + 32 + 32 + 32. */
    const val NOTIFICATION_PREIMAGE_LENGTH = 159

    /**
     * The preimage every authority record is signed over.
     *
     * @param type the record type, whose magic is the signature domain.
     * @param challenge the 32 bytes `POST /account/authority/challenge` minted for this transition.
     * @param canonicalBytes the record's bytes, which are also the bytes submitted, never a re-encoding.
     */
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

    /**
     * The preimage an authority device signs to approve an action reached from a browser (decision 6): the
     * domain, the 34 accountId bytes, the pending approval id, the action digest and the challenge.
     *
     * Every element is fixed length, so no field can be shifted into another. The action digest is what
     * makes the approval specific: a malicious page can start an approval the user never wanted, and what
     * defends that is the four-character code plus a device-side description of this exact action, on a
     * screen the page does not control.
     */
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

    /**
     * The preimage an install signs to prove it holds the device key its registration names: the domain, the
     * 34 accountId bytes, the SHA-256 of the installation id, the device key and a challenge minted for
     * `NOTIFY`.
     *
     * The installation id is hashed rather than carried, so the bytes signed here are a fixed length whatever
     * the id is, and the id itself is not repeated inside a value that ends up in a log.
     */
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
