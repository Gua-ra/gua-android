/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.sync

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiRoomListService
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiTaskHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.matrix.rustcomponents.sdk.NoHandle
import org.matrix.rustcomponents.sdk.RoomListService
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.SyncServiceStateObserver
import org.matrix.rustcomponents.sdk.TaskHandle

class RustSyncServiceTest {
    private class RecordingFfiSyncService : SyncService(NoHandle) {
        var startCalls = 0
        var stopCalls = 0
        override fun roomListService(): RoomListService = FakeFfiRoomListService()
        override fun state(listener: SyncServiceStateObserver): TaskHandle = FakeFfiTaskHandle()
        override suspend fun start() {
            startCalls++
        }

        override suspend fun stop() {
            stopCalls++
        }
    }

    @Test
    fun `a held identity reset suppresses every start and keeps stops working`() = runTest {
        val inner = RecordingFfiSyncService()
        val hold = MutableStateFlow(true)
        val sut = RustSyncService(
            inner = inner,
            dispatcher = StandardTestDispatcher(testScheduler),
            sessionCoroutineScope = backgroundScope,
            identityResetHold = hold,
        )

        sut.startSync()
        assertThat(inner.startCalls).isEqualTo(0)

        sut.stopSync()
        assertThat(inner.stopCalls).isEqualTo(1)

        hold.value = false
        sut.startSync()
        assertThat(inner.startCalls).isEqualTo(1)
    }
}
