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
 * GUA FORK: keeps own-user `/keys/query` traffic away from an identity reset.
 *
 * Between [EncryptionService.startIdentityReset] creating a new private cross-signing identity
 * locally and the authenticated upload landing, a `/keys/query` answered with the old identity
 * makes the SDK clear the new private keys, and the upload then succeeds anyway
 * (matrix-rust-sdk#4728). The account is left with an identity no device holds. The guard holds
 * the encryption sync stopped, suppresses every sync start and stands notification handling down
 * for the whole reset. Android runs everything in one process, so an in-memory hold is the whole
 * mechanism; there is no second process to signal.
 *
 * Invariants:
 * - [acquire] sets the hold before it stops the sync, so no start can slip in between, and it
 *   returns only once the stop has been awaited;
 * - release is idempotent and is the only place that restarts the sync;
 * - a reset call started through [runReset] keeps the guard held until it returns. A UI that stops
 *   waiting must not release it: the coroutine bindings drop the SDK's future on cancellation, and
 *   an upload already on the wire still lands.
 */
interface IdentityResetGuard {
    /** True from [acquire] until the guard is released. Sync starts and notification fetches consult it. */
    val isHeld: StateFlow<Boolean>

    /**
     * Sets the hold and stops the encryption sync, awaiting the stop. True once the guard is held
     * and free to be used, also when it was held already. False when a reset call started under it
     * is still running: minting another identity under a running upload would race it, so the
     * caller must not mint anything.
     */
    suspend fun acquire(): Boolean

    /**
     * Runs the reset call under the guard and returns it, to be awaited by whoever still cares.
     * The guard is released when the call returns, whether or not anyone is waiting. A second call
     * while one is running returns the running one instead of starting another reset.
     */
    fun runReset(operation: suspend () -> Result<Unit>): Deferred<Result<Unit>>

    /** Releases the guard if no reset call is in flight. A no-op otherwise, and after any release. */
    suspend fun releaseIfIdle()
}

/** GUA FORK: a notification fetch refused because an identity reset holds the sync. */
class IdentityResetInProgressException : Exception("Notifications stand down while an identity reset is in progress")
