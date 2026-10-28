/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.space.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.spaces.FakeSpaceService
import io.element.android.libraries.matrix.test.sync.FakeSyncService
import io.element.android.libraries.previewutils.room.aSpaceRoom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSpacesSwitchTest {
    @Test
    fun `an account already in a space with no stored value starts with Spaces on`() = runTest {
        val spaceService = FakeSpaceService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(spaceService = spaceService, writes = writes)
        runCurrent()

        spaceService.emitTopLevelSpaces(listOf(aSpaceRoom(state = CurrentUserMembership.JOINED)))
        runCurrent()

        assertThat(switch.isEnabled.value).isTrue()
        assertThat(writes).containsExactly("""{"enabled":true}""")
    }

    @Test
    fun `a stored false is kept when the account is in a space`() = runTest {
        val spaceService = FakeSpaceService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(spaceService = spaceService, stored = { """{"enabled":false}""" }, writes = writes)
        runCurrent()

        spaceService.emitTopLevelSpaces(listOf(aSpaceRoom(state = CurrentUserMembership.JOINED)))
        runCurrent()

        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).isEmpty()
    }

    @Test
    fun `a space invite alone does not turn Spaces on`() = runTest {
        val spaceService = FakeSpaceService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(spaceService = spaceService, writes = writes)
        runCurrent()

        spaceService.emitTopLevelSpaces(listOf(aSpaceRoom(state = CurrentUserMembership.INVITED)))
        runCurrent()

        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).isEmpty()
    }

    @Test
    fun `a value changed on another phone is read when sync starts`() = runTest {
        val syncService = FakeSyncService()
        var stored: String? = null
        val switch = createSwitch(syncService = syncService, stored = { stored })
        runCurrent()

        stored = """{"enabled":true}"""
        syncService.emitSyncState(SyncState.Running)
        runCurrent()

        assertThat(switch.isEnabled.value).isTrue()
    }

    @Test
    fun `setEnabled keeps the previous value when the write fails`() = runTest {
        val switch = createSwitch(writeResult = Result.failure(Exception("offline")))
        runCurrent()

        val result = switch.setEnabled(true)

        assertThat(result.isFailure).isTrue()
        assertThat(switch.isEnabled.value).isFalse()
    }

    private fun TestScope.createSwitch(
        spaceService: FakeSpaceService = FakeSpaceService(),
        syncService: FakeSyncService = FakeSyncService(),
        stored: () -> String? = { null },
        writeResult: Result<Unit> = Result.success(Unit),
        writes: MutableList<String> = mutableListOf(),
    ) = DefaultSpacesSwitch(
        matrixClient = FakeMatrixClient(
            spaceService = spaceService,
            syncService = syncService,
            getAccountDataLambda = { Result.success(stored()) },
            setAccountDataLambda = { _, content ->
                writes += content
                writeResult
            },
        ),
        sessionCoroutineScope = backgroundScope,
    )
}
