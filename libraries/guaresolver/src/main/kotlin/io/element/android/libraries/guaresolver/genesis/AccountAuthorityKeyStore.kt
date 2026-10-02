/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * The device-only account authority key pair and the recovery authority key committed beside it.
 * The keys are generated on device before the OIDC flow starts and never leave it. There is no escrow
 * and no sync: a lost device loses authority.
 *
 * There are two slots. The signup slot holds the pair minted for the signup in flight, which owns
 * nothing until the server attaches it, so a later signup may replace it and [clear] may drop it.
 * The attached slot holds the pair that owns an account and is never overwritten or deleted.
 * [markAttached] moves a pair from the first slot to the second.
 */
interface AccountAuthorityKeyStore {
    /** True when a pair for the signup in flight has been generated and is still readable. */
    suspend fun hasKeyPair(): Boolean

    /**
     * Generates a fresh authority and recovery key pair for a signup, replacing any earlier pair in the
     * signup slot. An attached pair is not touched. Returns the two raw 32-byte Ed25519 public keys, the
     * only halves that ever leave here.
     */
    suspend fun createKeyPair(): AccountAuthorityPublicKeys

    /** The public halves of the pair in the signup slot, or null when none is stored. */
    suspend fun publicKeys(): AccountAuthorityPublicKeys?

    /**
     * Records that the pair in the signup slot now owns [accountId], moving it to the attached slot
     * where no later signup and no [clear] can reach it. Idempotent for the same account.
     *
     * @throws IllegalStateException when the signup slot is empty, or when a different account is
     * already attached on this device. Promoting over that pair would destroy its authority.
     */
    suspend fun markAttached(accountId: AccountId)

    /** The accountId owned by the attached pair, or null when no pair on this device is attached. */
    suspend fun attachedAccountId(): String?

    /** The public halves of the attached pair, or null when no pair is attached. */
    suspend fun attachedPublicKeys(): AccountAuthorityPublicKeys?

    /**
     * Signs [message] with the account authority key of the signup in flight and returns the 64-byte
     * detached Ed25519 signature.
     *
     * @throws IllegalStateException when the signup slot is empty, so a missing key is never mistaken
     * for a successful signature.
     */
    suspend fun signWithAuthorityKey(message: ByteArray): ByteArray

    /** Forgets the pair in the signup slot. An attached pair is kept. */
    suspend fun clear()
}

/**
 * The two raw 32-byte Ed25519 public keys a suite 0x01 genesis commits. Only public halves travel in
 * this type; the private halves never leave [AccountAuthorityKeyStore].
 */
class AccountAuthorityPublicKeys(
    private val authority: ByteArray,
    private val recovery: ByteArray,
) {
    fun authorityPublicKey(): ByteArray = authority.copyOf()
    fun recoveryAuthorityPublicKey(): ByteArray = recovery.copyOf()
}
