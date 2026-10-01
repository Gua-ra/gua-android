/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/** Fixed-length preimages for the registration and attach proofs, both signed by the authority key. */
object GenesisProofs {
    const val GENESIS_PROOF_DOMAIN = "gua-account-genesis-proof.v1"

    const val ATTACH_PROOF_DOMAIN = "gua-account-attach-proof.v1"

    const val ATTACH_CHALLENGE_LENGTH = 32

    private val genesisDomainBytes = GENESIS_PROOF_DOMAIN.toByteArray(Charsets.US_ASCII)
    private val attachDomainBytes = ATTACH_PROOF_DOMAIN.toByteArray(Charsets.US_ASCII)

    const val ATTACH_PREIMAGE_LENGTH = 93

    fun genesisProofPreimage(canonicalBytes: ByteArray): ByteArray = genesisDomainBytes + canonicalBytes

    fun attachProofPreimage(challenge: ByteArray, accountId: AccountId): ByteArray {
        require(challenge.size == ATTACH_CHALLENGE_LENGTH) { "attach challenge is $ATTACH_CHALLENGE_LENGTH bytes" }
        val raw = accountId.rawBytes()
        val preimage = ByteArray(ATTACH_PREIMAGE_LENGTH)
        attachDomainBytes.copyInto(preimage, 0)
        challenge.copyInto(preimage, attachDomainBytes.size)
        raw.copyInto(preimage, attachDomainBytes.size + ATTACH_CHALLENGE_LENGTH)
        return preimage
    }
}
