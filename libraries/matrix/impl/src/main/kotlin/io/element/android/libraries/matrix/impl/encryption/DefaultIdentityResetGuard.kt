/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.encryption.IdentityResetGuard
import io.element.android.libraries.matrix.api.sync.SyncService
import io.element.android.libraries.matrix.api.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The session's [IdentityResetGuard]. See the interface for what it protects.
 *
 * [hold] is shared with the sync service and the notification service, which consult it before
 * starting a sync or fetching an event; the guard is its only writer. The sync is stopped twice
 * because `SyncService.start()` and `stop()` serialise on one lock inside the SDK: a start that
 * was already inside the SDK when the hold went up runs after the first stop and is caught by the
 * second. The SDK also resumes an offline sync on its own; an awaited stop leaves it Idle, which is
 * the one state it does not leave by itself.
 *
 * Every transition of the hold happens under [transitions], and a reset call checks the hold under
 * that same lock before it starts, so a guard released in the meantime (a screen torn down by a
 * notification tap while the handle was being minted) refuses the call instead of running it
 * with the sync live.
 */
class DefaultIdentityResetGuard(
    private val syncService: SyncService,
    private val sessionCoroutineScope: CoroutineScope,
    private val hold: MutableStateFlow<Boolean> = MutableStateFlow(false),
    private val settleDelay: Duration = 100.milliseconds,
) : IdentityResetGuard {
    override val isHeld: StateFlow<Boolean> = hold.asStateFlow()

    /** Serialises acquisition, release and the pre-call check, so none can overlap another. */
    private val transitions = Mutex()
    private val inFlightLock = Any()
    private var inFlight: Deferred<Result<Unit>>? = null

    override suspend fun acquire(): Boolean = transitions.withLock {
        if (isResetCallRunning()) return@withLock false
        if (hold.value) return@withLock true
        hold.value = true
        syncService.stopSync()
        delay(settleDelay)
        if (syncService.syncState.value == SyncState.Running) {
            Timber.w("The sync started again during identity reset acquisition; stopping it once more.")
        }
        syncService.stopSync()
        true
    }

    override fun runReset(operation: suspend () -> Result<Unit>): Deferred<Result<Unit>> {
        synchronized(inFlightLock) {
            inFlight?.takeIf { it.isActive }?.let { return it }
            val deferred = sessionCoroutineScope.async {
                val held = transitions.withLock { hold.value }
                if (!held) {
                    Timber.e("The identity reset guard was released before the reset call started; refusing to run it unguarded.")
                    return@async Result.failure(IllegalStateException("The identity reset guard was released before the reset call started"))
                }
                try {
                    runCatchingExceptions { operation().getOrThrow() }
                } finally {
                    // Whatever happened to the call, and even if the session is going away, the
                    // sync is not left stopped behind a hold nobody owns any more.
                    withContext(NonCancellable) { release() }
                }
            }
            inFlight = deferred
            return deferred
        }
    }

    override suspend fun releaseIfIdle() = transitions.withLock {
        if (isResetCallRunning()) return@withLock
        releaseLocked()
    }

    private suspend fun release() = transitions.withLock {
        releaseLocked()
    }

    /** The one terminal ordering: suppression off, then the sync restarted. Idempotent. Caller holds [transitions]. */
    private suspend fun releaseLocked() {
        if (!hold.value) return
        hold.value = false
        syncService.startSync()
    }

    private fun isResetCallRunning(): Boolean = synchronized(inFlightLock) { inFlight?.isActive == true }
}
