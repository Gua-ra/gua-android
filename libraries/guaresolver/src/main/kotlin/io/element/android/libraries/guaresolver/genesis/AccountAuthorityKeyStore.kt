/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * GUA FORK: the device-only account authority key pair ADM-008 decision 5 commits, and the recovery
 * authority key framework 0x01 commits beside it.
 *
 * The keys are generated on device before the OIDC flow starts and never leave it. There is no escrow
 * and no sync: ADM-008's own consequences section states that a lost device loses authority, which is
 * the deliberate cost of the key being unexportable.
 */
interface AccountAuthorityKeyStore {
    /** True when a key pair has already been generated and is still readable. */
    suspend fun hasKeyPair(): Boolean

    /**
     * Generates a fresh authority and recovery key pair, replacing any pair that was never attached.
     *
     * @return the two raw 32-byte Ed25519 PUBLIC keys, which are the only halves that ever leave here.
     */
    suspend fun createKeyPair(): AccountAuthorityPublicKeys

    /** The public halves of the stored pair, or null when none is stored. */
    suspend fun publicKeys(): AccountAuthorityPublicKeys?

    /**
     * Signs [message] with the account authority key.
     *
     * @return the 64-byte detached Ed25519 signature.
     * @throws IllegalStateException when no key pair is stored, so a caller can never mistake a missing
     * key for a successful signature.
     */
    suspend fun signWithAuthorityKey(message: ByteArray): ByteArray

    /** Forgets the stored pair. */
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
