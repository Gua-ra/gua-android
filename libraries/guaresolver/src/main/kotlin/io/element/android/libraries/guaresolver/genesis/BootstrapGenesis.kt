/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/** Keeps the exact bytes it was decoded from. The client never mints one; the server does. */
class BootstrapGenesis internal constructor(
    val version: Int,
    val suite: Int,
    private val entropyBytes: ByteArray,
    private val bytes: ByteArray,
) {
    fun entropy(): ByteArray = entropyBytes.copyOf()

    fun canonicalBytes(): ByteArray = bytes.copyOf()

    fun accountId(): AccountId = AccountId.derive(AccountId.CLASS_BOOTSTRAP, bytes)

    companion object {
        const val LENGTH = 22

        const val MAGIC = "GUAB"

        const val VERSION = 0x01

        const val SUITE_NONE = 0x00

        const val ENTROPY_LENGTH = 16
    }
}
