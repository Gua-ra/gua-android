/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * GUA FORK: the two possession proofs ADM-008 defines, and the fixed-length preimages they cover. Port
 * of the identity-service `GenesisProofs`; this side builds the preimages and signs them, the server
 * side verifies them.
 *
 * REGISTRATION PROOF (decision 3). An Ed25519 signature by the authority key over the ASCII domain
 * `gua-account-genesis-proof.v1` followed by the canonical bytes. It proves the registrant holds the key
 * and is not part of the genesis. No identifier appears in the preimage: an MXID there would put an
 * identifier into the id.
 *
 * ATTACH PROOF (decision 6). An Ed25519 signature by the same committed authority key over the domain
 * `gua-account-attach-proof.v1`, then the 32 server-chosen challenge bytes, then the 34 raw accountId
 * bytes. Every element is fixed length, so no field can be shifted into another: 27 + 32 + 34. A handle
 * alone attaches nothing, because anyone can compose an authorize URL carrying someone else's handle;
 * only this signature shows that the party which registered the genesis held its key and was present in
 * this login session.
 */
object GenesisProofs {
    /** 28 ASCII bytes. */
    const val GENESIS_PROOF_DOMAIN = "gua-account-genesis-proof.v1"

    /** 27 ASCII bytes, as ADM-008 decision 6 states. */
    const val ATTACH_PROOF_DOMAIN = "gua-account-attach-proof.v1"

    /** Server-chosen challenge length, in bytes. */
    const val ATTACH_CHALLENGE_LENGTH = 32

    private val genesisDomainBytes = GENESIS_PROOF_DOMAIN.toByteArray(Charsets.US_ASCII)
    private val attachDomainBytes = ATTACH_PROOF_DOMAIN.toByteArray(Charsets.US_ASCII)

    /** 27 + 32 + 34. */
    const val ATTACH_PREIMAGE_LENGTH = 93

    /** Domain bytes then the canonical bytes. */
    fun genesisProofPreimage(canonicalBytes: ByteArray): ByteArray = genesisDomainBytes + canonicalBytes

    /**
     * Domain bytes, then the challenge, then the raw accountId bytes. Fixed length throughout.
     *
     * @param challenge the 32 server-chosen bytes held against the login session.
     * @param accountId the accountId derived from the registered genesis.
     */
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
