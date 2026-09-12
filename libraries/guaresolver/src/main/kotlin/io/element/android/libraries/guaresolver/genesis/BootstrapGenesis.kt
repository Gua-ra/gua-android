/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * GUA FORK: a decoded `BootstrapGenesis` (ADM-008 suite 0x00), together with the exact bytes it was
 * decoded from. Port of the identity-service `BootstrapGenesis`.
 *
 * It commits nothing and makes no ADM-001 L4 claim. Its whole job is to give an account that predates
 * account authority an accountId that is re-derivable and auditable, with the root class byte 0x00
 * marking it as a bootstrap account (L5 path B1). The client never mints one: the server does, for a
 * signup that presented no handle. It is decoded here so this port reproduces every published vector.
 */
class BootstrapGenesis internal constructor(
    val version: Int,
    val suite: Int,
    private val entropyBytes: ByteArray,
    private val bytes: ByteArray,
) {
    fun entropy(): ByteArray = entropyBytes.copyOf()

    /** The bytes as received. Stored so the id stays re-derivable and auditable. */
    fun canonicalBytes(): ByteArray = bytes.copyOf()

    /** Bootstrap-class accountId over those bytes. */
    fun accountId(): AccountId = AccountId.derive(AccountId.CLASS_BOOTSTRAP, bytes)

    companion object {
        /** Total canonical length. Any other length is rejected. */
        const val LENGTH = 22

        /** ASCII `GUAB`, the domain separator. */
        const val MAGIC = "GUAB"

        const val VERSION = 0x01

        /** No authority key. */
        const val SUITE_NONE = 0x00

        const val ENTROPY_LENGTH = 16
    }
}
