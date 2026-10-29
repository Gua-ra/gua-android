/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.space.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.spaces.SpaceRoom
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.libraries.matrix.test.spaces.FakeSpaceService
import io.element.android.libraries.previewutils.room.aSpaceRoom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Optional
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSpacesSwitchTest {
    @Test
    fun `an account already in a space with no stored value starts with Spaces on`() = runTest {
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        // The flow never emits: the decision reads the joined spaces directly.
        val switch = createSwitch(spaceService = aSpaceServiceInASpace(), roomListService = roomListService, writes = writes)
        runCurrent()

        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(switch.isEnabled.value).isTrue()
        assertThat(writes).containsExactly("""{"enabled":true}""")
    }

    @Test
    fun `the default waits for the room list to be running`() = runTest {
        var queries = 0
        val spaceService = FakeSpaceService(
            topLevelSpacesResult = {
                queries++
                Result.success(listOf(aSpaceRoom(state = CurrentUserMembership.JOINED)))
            },
        )
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(spaceService = spaceService, roomListService = roomListService, writes = writes)
        runCurrent()

        roomListService.emitSyncUpdate()
        roomListService.emitSyncUpdate()
        settle()

        assertThat(queries).isEqualTo(0)
        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).isEmpty()

        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(queries).isEqualTo(1)
        assertThat(writes).containsExactly("""{"enabled":true}""")
    }

    @Test
    fun `a failed read when the room list runs writes nothing and the next run decides`() = runTest {
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        var readFails = true
        val switch = createSwitch(
            spaceService = aSpaceServiceInASpace(),
            roomListService = roomListService,
            readResult = { if (readFails) Result.failure(Exception("store")) else Result.success(null) },
            writes = writes,
        )
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).isEmpty()

        readFails = false
        roomListService.postState(RoomListService.State.Idle)
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(switch.isEnabled.value).isTrue()
        assertThat(writes).containsExactly("""{"enabled":true}""")
    }

    @Test
    fun `a failed membership read decides nothing`() = runTest {
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(
            spaceService = FakeSpaceService(topLevelSpacesResult = { Result.failure(Exception("store")) }),
            roomListService = roomListService,
            writes = writes,
        )
        runCurrent()

        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).isEmpty()
    }

    @Test
    fun `opening Settings re-reads the stored value but never decides the default`() = runTest {
        var queries = 0
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(
            spaceService = FakeSpaceService(
                topLevelSpacesResult = {
                    queries++
                    Result.success(listOf(aSpaceRoom(state = CurrentUserMembership.JOINED)))
                },
            ),
            roomListService = roomListService,
            writes = writes,
        )
        runCurrent()

        switch.refresh()
        settle()

        assertThat(queries).isEqualTo(0)
        assertThat(writes).isEmpty()
        assertThat(switch.isEnabled.value).isFalse()
    }

    @Test
    fun `an account with no spaces stores off once`() = runTest {
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(roomListService = roomListService, writes = writes)
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).containsExactly("""{"enabled":false}""")

        // The echo has not arrived yet, so the store still reads nothing.
        roomListService.postState(RoomListService.State.Idle)
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        roomListService.emitSyncUpdate()
        settle()

        assertThat(writes).containsExactly("""{"enabled":false}""")
    }

    @Test
    fun `a failed migration is retried when the room list runs again`() = runTest {
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(
            spaceService = aSpaceServiceInASpace(),
            roomListService = roomListService,
            writes = writes,
            writeResult = { if (writes.size == 1) Result.failure(Exception("offline")) else Result.success(Unit) },
        )
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(switch.isEnabled.value).isTrue()
        assertThat(writes).hasSize(1)

        roomListService.postState(RoomListService.State.Idle)
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(switch.isEnabled.value).isTrue()
        assertThat(writes).hasSize(2)
    }

    @Test
    fun `a stored false is kept when the account is in a space`() = runTest {
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(
            spaceService = aSpaceServiceInASpace(),
            roomListService = roomListService,
            stored = { """{"enabled":false}""" },
            writes = writes,
        )
        runCurrent()

        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).isEmpty()
    }

    @Test
    fun `a space joined after the default was stored does not turn Spaces on`() = runTest {
        var spaces = emptyList<SpaceRoom>()
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(
            spaceService = FakeSpaceService(topLevelSpacesResult = { Result.success(spaces) }),
            roomListService = roomListService,
            writes = writes,
        )
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        settle()

        spaces = listOf(aSpaceRoom(state = CurrentUserMembership.JOINED))
        roomListService.postState(RoomListService.State.Idle)
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).containsExactly("""{"enabled":false}""")
    }

    @Test
    fun `a user choice made while the default is being decided wins`() = runTest {
        val spaces = CompletableDeferred<List<SpaceRoom>>()
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(
            spaceService = FakeSpaceService(topLevelSpacesResult = { Result.success(spaces.await()) }),
            roomListService = roomListService,
            writes = writes,
        )
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        runCurrent()

        launch { switch.setEnabled(false) }
        runCurrent()
        spaces.complete(listOf(aSpaceRoom(state = CurrentUserMembership.JOINED)))
        settle()

        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).containsExactly("""{"enabled":false}""")
    }

    @Test
    fun `a value changed on another phone is read when the room list is running`() = runTest {
        val roomListService = FakeRoomListService()
        var stored: String? = """{"enabled":false}"""
        val switch = createSwitch(roomListService = roomListService, stored = { stored })
        runCurrent()

        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isFalse()

        stored = """{"enabled":true}"""
        roomListService.postState(RoomListService.State.Running)
        runCurrent()

        assertThat(switch.isEnabled.value).isTrue()
    }

    @Test
    fun `a value changed on another phone is read on the next sync update while the room list stays running`() = runTest {
        val roomListService = FakeRoomListService()
        var stored: String? = """{"enabled":false}"""
        val switch = createSwitch(roomListService = roomListService, stored = { stored })
        roomListService.postState(RoomListService.State.Running)
        runCurrent()
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isFalse()

        stored = """{"enabled":true}"""
        roomListService.emitSyncUpdate()
        runCurrent()

        assertThat(switch.isEnabled.value).isTrue()
    }

    @Test
    fun `a sync update that has not brought this phone's write back does not undo it`() = runTest {
        val roomListService = FakeRoomListService()
        var stored: String? = """{"enabled":false}"""
        val switch = createSwitch(roomListService = roomListService, stored = { stored })
        roomListService.postState(RoomListService.State.Running)
        runCurrent()

        launch { switch.setEnabled(true) }
        runCurrent()
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isTrue()

        settle()
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isTrue()

        stored = """{"enabled":true}"""
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isTrue()

        stored = """{"enabled":false}"""
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isFalse()
    }

    @Test
    fun `a stale read does not undo a value this phone is writing or has just written`() = runTest {
        val roomListService = FakeRoomListService()
        var stored: String? = """{"enabled":false}"""
        val switch = createSwitch(roomListService = roomListService, stored = { stored })
        roomListService.postState(RoomListService.State.Running)
        runCurrent()

        launch { switch.setEnabled(true) }
        runCurrent()
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isTrue()

        settle()
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isTrue()

        stored = """{"enabled":true}"""
        roomListService.postState(RoomListService.State.Idle)
        runCurrent()
        roomListService.postState(RoomListService.State.Running)
        runCurrent()
        assertThat(switch.isEnabled.value).isTrue()

        stored = """{"enabled":false}"""
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isFalse()
    }

    @Test
    fun `the write is not cancelled when the caller is cancelled`() = runTest {
        val writes = mutableListOf<String>()
        val switch = createSwitch(writes = writes)
        runCurrent()

        val caller = launch { switch.setEnabled(true) }
        runCurrent()
        caller.cancel()
        settle()

        assertThat(writes).containsExactly("""{"enabled":true}""")
        assertThat(switch.isEnabled.value).isTrue()
    }

    @Test
    fun `setEnabled keeps the previous value when the write fails`() = runTest {
        val switch = createSwitch(writeResult = { Result.failure(Exception("offline")) })
        runCurrent()

        val result = switch.setEnabled(true)

        assertThat(result.isFailure).isTrue()
        assertThat(switch.isEnabled.value).isFalse()
    }

    @Test
    fun `a failed write does not undo a newer request`() = runTest {
        val writes = mutableListOf<String>()
        val switch = createSwitch(
            writes = writes,
            writeResult = { if (writes.size == 1) Result.failure(Exception("offline")) else Result.success(Unit) },
        )
        runCurrent()

        launch { switch.setEnabled(true) }
        launch { switch.setEnabled(false) }
        launch { switch.setEnabled(true) }
        settle()

        assertThat(writes).containsExactly("""{"enabled":true}""", """{"enabled":false}""", """{"enabled":true}""").inOrder()
        assertThat(switch.isEnabled.value).isTrue()
    }

    @Test
    fun `enableWhenJoined turns Spaces on once the membership is joined`() = runTest {
        val roomInfo = MutableStateFlow<Optional<RoomInfo>>(Optional.empty())
        val writes = mutableListOf<String>()
        val switch = createSwitch(writes = writes, roomInfoFlow = roomInfo)
        runCurrent()

        switch.enableWhenJoined(A_ROOM_ID)
        roomInfo.value = Optional.of(aRoomInfo(currentUserMembership = CurrentUserMembership.INVITED))
        settle()
        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).isEmpty()

        roomInfo.value = Optional.of(aRoomInfo(currentUserMembership = CurrentUserMembership.JOINED))
        settle()
        assertThat(switch.isEnabled.value).isTrue()
        assertThat(writes).containsExactly("""{"enabled":true}""")
    }

    @Test
    fun `enableWhenJoined does not write when Spaces is already on`() = runTest {
        val roomListService = FakeRoomListService()
        val writes = mutableListOf<String>()
        val switch = createSwitch(
            roomListService = roomListService,
            stored = { """{"enabled":true}""" },
            writes = writes,
            roomInfoFlow = flowOf(Optional.of(aRoomInfo(currentUserMembership = CurrentUserMembership.JOINED))),
        )
        runCurrent()
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(switch.isEnabled.value).isTrue()

        switch.enableWhenJoined(A_ROOM_ID)
        settle()

        assertThat(switch.isEnabled.value).isTrue()
        assertThat(writes).isEmpty()
    }

    @Test
    fun `enableWhenJoined gives up when the membership is not joined in time`() = runTest {
        val roomInfo = MutableStateFlow(Optional.of(aRoomInfo(currentUserMembership = CurrentUserMembership.INVITED)))
        val writes = mutableListOf<String>()
        val switch = createSwitch(writes = writes, roomInfoFlow = roomInfo)
        runCurrent()

        switch.enableWhenJoined(A_ROOM_ID)
        advanceTimeBy(ENABLE_WHEN_JOINED_TIMEOUT + 1.seconds)
        runCurrent()
        roomInfo.value = Optional.of(aRoomInfo(currentUserMembership = CurrentUserMembership.JOINED))
        settle()

        assertThat(switch.isEnabled.value).isFalse()
        assertThat(writes).isEmpty()
    }

    // advanceUntilIdle() does not run backgroundScope work, where the switch runs.
    private fun TestScope.settle() {
        advanceTimeBy(10)
        runCurrent()
    }

    private fun aSpaceServiceInASpace() = FakeSpaceService(topLevelSpacesResult = { Result.success(listOf(aSpaceRoom(state = CurrentUserMembership.JOINED))) })

    private fun TestScope.createSwitch(
        spaceService: FakeSpaceService = FakeSpaceService(),
        roomListService: FakeRoomListService = FakeRoomListService(),
        stored: () -> String? = { null },
        readResult: () -> Result<String?> = { Result.success(stored()) },
        writes: MutableList<String> = mutableListOf(),
        writeResult: () -> Result<Unit> = { Result.success(Unit) },
        roomInfoFlow: Flow<Optional<RoomInfo>> = flowOf(Optional.empty()),
    ) = DefaultSpacesSwitch(
        matrixClient = FakeMatrixClient(
            spaceService = spaceService,
            roomListService = roomListService,
            getAccountDataLambda = { readResult() },
            setAccountDataLambda = { _, content ->
                writes += content
                writeResult()
            },
        ).apply {
            getRoomInfoFlowLambda = { roomInfoFlow }
        },
        sessionCoroutineScope = backgroundScope,
    )
}
