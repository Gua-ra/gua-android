/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * GUA FORK: a decoded `AccountGenesis` (ADM-008 suite 0x01), together with the exact bytes it was
 * decoded from. Port of the identity-service `AccountGenesis`.
 *
 * It commits the initial authority key, the algorithm identifiers and the initial recovery authority and
 * framework, and it holds no identifier and no homeserver (ADM-001 L4). Every accessor hands back a
 * copy, so nothing downstream can mutate the bytes the accountId is derived from.
 */
class AccountGenesis internal constructor(
    val genesisVersion: Int,
    val suite: Int,
    private val authorityKey: ByteArray,
    val recoveryFrameworkId: Int,
    private val recoveryKey: ByteArray,
    private val entropyBytes: ByteArray,
    private val bytes: ByteArray,
) {
    /** The raw 32-byte Ed25519 account authority key. */
    fun authorityPublicKey(): ByteArray = authorityKey.copyOf()

    fun recoveryAuthorityPublicKey(): ByteArray = recoveryKey.copyOf()

    fun entropy(): ByteArray = entropyBytes.copyOf()

    /** The bytes as received. The accountId is the hash of these, never of a re-encoding. */
    fun canonicalBytes(): ByteArray = bytes.copyOf()

    /** Genesis-rooted accountId over the received bytes. */
    fun accountId(): AccountId = AccountId.derive(AccountId.CLASS_GENESIS, bytes)

    companion object {
        /** Total canonical length. Any other length is rejected. */
        const val LENGTH = 87

        /** ASCII `GUAG`, the domain separator. */
        const val MAGIC = "GUAG"

        const val VERSION = 0x01

        /** Ed25519 authority, Ed25519 recovery, SHA-256. */
        const val SUITE_ED25519_SHA256 = 0x01

        /** One committed recovery authority key (ADM-008 decision 4). */
        const val RECOVERY_FRAMEWORK_COMMITTED_KEY = 0x01

        const val ENTROPY_LENGTH = 16
    }
}
