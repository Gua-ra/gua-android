/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/** Keeps the exact bytes it was decoded from. Accessors return copies. */
class AccountGenesis internal constructor(
    val genesisVersion: Int,
    val suite: Int,
    private val authorityKey: ByteArray,
    val recoveryFrameworkId: Int,
    private val recoveryKey: ByteArray,
    private val entropyBytes: ByteArray,
    private val bytes: ByteArray,
) {
    fun authorityPublicKey(): ByteArray = authorityKey.copyOf()

    fun recoveryAuthorityPublicKey(): ByteArray = recoveryKey.copyOf()

    fun entropy(): ByteArray = entropyBytes.copyOf()

    /** The bytes as received. The accountId is the hash of these, never of a re-encoding. */
    fun canonicalBytes(): ByteArray = bytes.copyOf()

    fun accountId(): AccountId = AccountId.derive(AccountId.CLASS_GENESIS, bytes)

    companion object {
        const val LENGTH = 87

        const val MAGIC = "GUAG"

        const val VERSION = 0x01

        /** Ed25519 authority, Ed25519 recovery, SHA-256. */
        const val SUITE_ED25519_SHA256 = 0x01

        const val RECOVERY_FRAMEWORK_COMMITTED_KEY = 0x01

        const val ENTROPY_LENGTH = 16
    }
}
