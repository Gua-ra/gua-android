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

class DefaultIdentityResetGuard(
    private val syncService: SyncService,
    private val sessionCoroutineScope: CoroutineScope,
    private val hold: MutableStateFlow<Boolean> = MutableStateFlow(false),
    private val settleDelay: Duration = 100.milliseconds,
) : IdentityResetGuard {
    override val isHeld: StateFlow<Boolean> = hold.asStateFlow()

    private val transitions = Mutex()
    private val inFlightLock = Any()
    private var inFlight: Deferred<Result<Unit>>? = null

    override suspend fun acquire(): Boolean = transitions.withLock {
        if (isResetCallRunning()) return@withLock false
        if (hold.value) return@withLock true
        hold.value = true
        syncService.stopSync()
        // Stopped twice: a start already inside the SDK when the hold went up runs after the first stop.
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

    private suspend fun releaseLocked() {
        if (!hold.value) return
        hold.value = false
        syncService.startSync()
    }

    private fun isResetCallRunning(): Boolean = synchronized(inFlightLock) { inFlight?.isActive == true }
}
