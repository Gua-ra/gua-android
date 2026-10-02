/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.sync.SyncService
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.test.sync.FakeSyncService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultIdentityResetGuardTest {
    private val events = mutableListOf<String>()
    private val hold = MutableStateFlow(false)
    private val syncService = FakeSyncService(initialSyncState = SyncState.Running).apply {
        stopSyncLambda = {
            events += if (hold.value) "stop(held)" else "stop(not held)"
            Result.success(Unit)
        }
        startSyncLambda = {
            events += if (hold.value) "start(held)" else "start"
            Result.success(Unit)
        }
    }

    @Test
    fun `acquire sets the hold, then stops the sync twice, before anything is minted`() = runTest {
        val guard = createGuard()

        guard.acquire()
        events += "mint"

        assertThat(events).containsExactly("stop(held)", "stop(held)", "mint").inOrder()
        assertThat(guard.isHeld.value).isTrue()
    }

    @Test
    fun `acquiring a held guard does nothing more`() = runTest {
        val guard = createGuard()

        guard.acquire()
        guard.acquire()

        assertThat(events).containsExactly("stop(held)", "stop(held)").inOrder()
    }

    @Test
    fun `releasing an idle guard lifts the hold before restarting the sync, once`() = runTest {
        val guard = createGuard()
        guard.acquire()
        events.clear()

        guard.releaseIfIdle()
        guard.releaseIfIdle()

        assertThat(events).containsExactly("start")
        assertThat(guard.isHeld.value).isFalse()
    }

    @Test
    fun `a running reset keeps the guard held until it returns`() = runTest {
        val guard = createGuard()
        guard.acquire()
        events.clear()
        val gate = CompletableDeferred<Unit>()

        val operation = guard.runReset {
            gate.await()
            Result.success(Unit)
        }

        assertThat(withTimeoutOrNull(20.milliseconds) { operation.await() }).isNull()
        assertThat(guard.isHeld.value).isTrue()
        assertThat(events).isEmpty()

        guard.releaseIfIdle()
        assertThat(guard.isHeld.value).isTrue()
        assertThat(events).isEmpty()

        val second = guard.runReset { error("a second reset must not start") }
        assertThat(second).isSameInstanceAs(operation)

        gate.complete(Unit)
        assertThat(operation.await().isSuccess).isTrue()
        assertThat(events).containsExactly("start")
        assertThat(guard.isHeld.value).isFalse()

        guard.releaseIfIdle()
        assertThat(events).containsExactly("start")
    }

    @Test
    fun `a reset that fails releases once`() = runTest {
        val guard = createGuard()
        guard.acquire()
        events.clear()

        val result = guard.runReset { Result.failure(IllegalStateException("refused")) }.await()

        assertThat(result.isFailure).isTrue()
        assertThat(events).containsExactly("start")
        assertThat(guard.isHeld.value).isFalse()
        guard.releaseIfIdle()
        assertThat(events).containsExactly("start")
    }

    @Test
    fun `a reset that throws releases once and reports the failure`() = runTest {
        val guard = createGuard()
        guard.acquire()
        events.clear()

        val result = guard.runReset { error("boom") }.await()

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(events).containsExactly("start")
        assertThat(guard.isHeld.value).isFalse()
    }

    @Test
    fun `acquire declines while a reset call from an earlier attempt is running`() = runTest {
        val guard = createGuard()
        assertThat(guard.acquire()).isTrue()
        val gate = CompletableDeferred<Unit>()
        val operation = guard.runReset {
            gate.await()
            Result.success(Unit)
        }
        events.clear()

        assertThat(guard.acquire()).isFalse()
        assertThat(events).isEmpty()

        gate.complete(Unit)
        operation.await()
        assertThat(guard.acquire()).isTrue()
    }

    @Test
    fun `a guard released before the call refuses to run it unguarded`() = runTest {
        val guard = createGuard()
        guard.acquire()
        guard.releaseIfIdle()
        events.clear()

        val result = guard.runReset {
            events += "operation"
            Result.success(Unit)
        }.await()

        assertThat(result.isFailure).isTrue()
        assertThat(events).isEmpty()
        assertThat(guard.isHeld.value).isFalse()
    }

    @Test
    fun `tearing the session down under a running reset still restarts the sync once`() = runTest {
        val session = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val guard = DefaultIdentityResetGuard(syncService = syncService, sessionCoroutineScope = session, hold = hold)
        guard.acquire()
        events.clear()
        val gate = CompletableDeferred<Unit>()
        guard.runReset {
            gate.await()
            Result.success(Unit)
        }
        runCurrent()

        session.cancel()
        runCurrent()

        assertThat(events).containsExactly("start")
        assertThat(guard.isHeld.value).isFalse()
    }

    @Test
    fun `acquire returns only once the stop has actually been awaited`() = runTest {
        val stopGate = CompletableDeferred<Unit>()
        var stops = 0
        val slowSyncService = object : SyncService {
            override val syncState: StateFlow<SyncState> = MutableStateFlow(SyncState.Running)
            override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
            override suspend fun startSync(): Result<Unit> = Result.success(Unit)
            override suspend fun stopSync(): Result<Unit> {
                stops++
                if (stops == 1) stopGate.await()
                return Result.success(Unit)
            }
        }
        val guard = DefaultIdentityResetGuard(syncService = slowSyncService, sessionCoroutineScope = backgroundScope, hold = hold)
        val acquisition = backgroundScope.async { guard.acquire() }
        runCurrent()

        assertThat(hold.value).isTrue()
        assertThat(acquisition.isCompleted).isFalse()

        stopGate.complete(Unit)
        assertThat(acquisition.await()).isTrue()
        assertThat(stops).isEqualTo(2)
    }

    @Test
    fun `a new reset can be run after a release`() = runTest {
        val guard = createGuard()
        guard.acquire()
        guard.runReset { Result.success(Unit) }.await()
        events.clear()

        guard.acquire()
        guard.runReset { Result.success(Unit) }.await()

        assertThat(events).containsExactly("stop(held)", "stop(held)", "start").inOrder()
    }

    private fun TestScope.createGuard() = DefaultIdentityResetGuard(
        syncService = syncService,
        sessionCoroutineScope = backgroundScope,
        hold = hold,
    )
}
