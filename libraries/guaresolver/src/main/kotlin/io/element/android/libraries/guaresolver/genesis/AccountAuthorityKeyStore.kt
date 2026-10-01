/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * The keys are generated on device and never leave it.
 * The signup slot may be replaced or cleared. The attached slot owns an account and is never overwritten or deleted.
 */
interface AccountAuthorityKeyStore {
    suspend fun hasKeyPair(): Boolean

    /** Replaces any pair in the signup slot. Returns the two raw 32-byte Ed25519 public keys. */
    suspend fun createKeyPair(): AccountAuthorityPublicKeys

    suspend fun publicKeys(): AccountAuthorityPublicKeys?

    /** Moves the signup pair to the attached slot. Idempotent for the same account; throws when a different account is already attached. */
    suspend fun markAttached(accountId: AccountId)

    suspend fun attachedAccountId(): String?

    suspend fun attachedPublicKeys(): AccountAuthorityPublicKeys?

    /** Throws [IllegalStateException] when the signup slot is empty. */
    suspend fun signWithAuthorityKey(message: ByteArray): ByteArray

    /** Forgets the pair in the signup slot. An attached pair is kept. */
    suspend fun clear()
}

class AccountAuthorityPublicKeys(
    private val authority: ByteArray,
    private val recovery: ByteArray,
) {
    fun authorityPublicKey(): ByteArray = authority.copyOf()
    fun recoveryAuthorityPublicKey(): ByteArray = recovery.copyOf()
}
