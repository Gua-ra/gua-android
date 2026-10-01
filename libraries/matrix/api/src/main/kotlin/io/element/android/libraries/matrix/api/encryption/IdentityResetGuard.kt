/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps the sync stopped and notification fetches refused for the whole of an identity reset.
 * An own-user `/keys/query` answered with the old identity before the upload lands makes the SDK clear the new private keys
 * (matrix-rust-sdk#4728).
 */
interface IdentityResetGuard {
    val isHeld: StateFlow<Boolean>

    /**
     * Sets the hold, then stops the sync and awaits the stop.
     * Returns false while a reset call is still running; the caller must then not mint a new identity.
     */
    suspend fun acquire(): Boolean

    /**
     * Runs the reset call and releases the guard when it returns, whether or not anyone still awaits it.
     * A call made while one is running returns the running one.
     */
    fun runReset(operation: suspend () -> Result<Unit>): Deferred<Result<Unit>>

    suspend fun releaseIfIdle()
}

class IdentityResetInProgressException : Exception("Notifications stand down while an identity reset is in progress")
