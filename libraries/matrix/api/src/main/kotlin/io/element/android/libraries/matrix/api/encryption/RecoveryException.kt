/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption

import io.element.android.libraries.matrix.api.exception.ClientException

sealed class RecoveryException(message: String) : Exception(message) {
    class SecretStorage(message: String) : RecoveryException(message)
    class Import(message: String) : RecoveryException(message)
    data object BackupExistsOnServer : RecoveryException("BackupExistsOnServer")

    /**
     * GUA FORK: the SDK's encryption initialisation had not finished within the bound, so the
     * operation was refused rather than risk creating a second key backup alongside the one the
     * initialisation creates. Nothing was minted; the caller may try again later.
     */
    data object E2eeInitializationPending : RecoveryException("E2eeInitializationPending")

    /**
     * GUA FORK: `enableRecovery` minted the secret store (the SDK reported its progress as done)
     * and then failed while recomputing the recovery state, which takes network round trips. The
     * store exists; only its confirmation is missing. A caller that would retry on failure must
     * treat this as a mint, never as "nothing happened", or it rotates the store it just made.
     */
    class MintedButUnconfirmed(val reason: Throwable) : RecoveryException("MintedButUnconfirmed")
    data class Client(val exception: ClientException) : RecoveryException(exception.message ?: "Unknown error")
}
