/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultChatThemeSettingTest {
    private val oceanTeal = ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Ocean), ChatBubbleColor.Teal)
    private val oceanTealContent = """{"bubble":"teal","kind":"preset","preset":"ocean","v":1}"""
    private val dawnBlue = ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Dawn), ChatBubbleColor.Blue)
    private val dawnBlueContent = """{"bubble":"blue","kind":"preset","preset":"dawn","v":1}"""

    @Test
    fun `the cached default shows before the store is read`() = runTest {
        val setting = createSetting(cache = InMemoryChatBackgroundCache(defaultTheme = oceanTealContent), readResult = { Result.failure(Exception("store")) })

        assertThat(setting.theme.value).isEqualTo(ChatTheme.STOCK)
        setting.preload()
        runCurrent()

        assertThat(setting.theme.value).isEqualTo(oceanTeal)
    }

    @Test
    fun `the stored default replaces the cached one at session start and is cached`() = runTest {
        val cache = InMemoryChatBackgroundCache(defaultTheme = oceanTealContent)
        val setting = createSetting(cache = cache, stored = { dawnBlueContent })

        setting.preload()
        runCurrent()

        assertThat(setting.theme.value).isEqualTo(dawnBlue)
        assertThat(cache.defaultTheme).isEqualTo(dawnBlueContent)
    }

    @Test
    fun `an empty store keeps the cached default until a sync confirms there is none`() = runTest {
        val roomListService = FakeRoomListService()
        val setting = createSetting(cache = InMemoryChatBackgroundCache(defaultTheme = oceanTealContent), roomListService = roomListService)

        setting.preload()
        runCurrent()
        assertThat(setting.theme.value).isEqualTo(oceanTeal)

        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(setting.theme.value).isEqualTo(ChatTheme.STOCK)
    }

    @Test
    fun `a default changed on another device is read after a sync`() = runTest {
        val roomListService = FakeRoomListService()
        var stored: String? = oceanTealContent
        val setting = createSetting(roomListService = roomListService, stored = { stored })
        setting.preload()
        runCurrent()
        assertThat(setting.theme.value).isEqualTo(oceanTeal)

        stored = "{}"
        roomListService.emitSyncUpdate()
        runCurrent()

        assertThat(setting.theme.value).isEqualTo(ChatTheme.STOCK)
    }

    @Test
    fun `the room list running again reads the store`() = runTest {
        val roomListService = FakeRoomListService()
        var stored: String? = null
        val setting = createSetting(roomListService = roomListService, stored = { stored })
        setting.preload()
        runCurrent()

        stored = dawnBlueContent
        roomListService.postState(RoomListService.State.Running)
        runCurrent()

        assertThat(setting.theme.value).isEqualTo(dawnBlue)
    }

    @Test
    fun `sync updates are read at most once every five seconds, and the last one is not lost`() = runTest {
        val roomListService = FakeRoomListService()
        var reads = 0
        var stored: String? = null
        val setting = createSetting(
            roomListService = roomListService,
            readResult = {
                reads++
                Result.success(stored)
            },
        )
        setting.preload()
        runCurrent()
        assertThat(reads).isEqualTo(1)

        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(reads).isEqualTo(2)

        stored = oceanTealContent
        repeat(3) {
            roomListService.emitSyncUpdate()
            advanceTimeBy(1.seconds)
            runCurrent()
        }
        assertThat(reads).isEqualTo(2)
        assertThat(setting.theme.value).isEqualTo(ChatTheme.STOCK)

        advanceTimeBy(CHAT_THEME_REFRESH_INTERVAL)
        runCurrent()
        assertThat(reads).isEqualTo(3)
        assertThat(setting.theme.value).isEqualTo(oceanTeal)
    }

    @Test
    fun `setting a default shows it at once, then stores and caches it`() = runTest {
        val cache = InMemoryChatBackgroundCache()
        val writes = mutableListOf<Pair<String, String>>()
        val setting = createSetting(cache = cache, writes = writes)
        setting.preload()
        runCurrent()

        launch { setting.set(dawnBlue) }
        runCurrent()
        assertThat(setting.theme.value).isEqualTo(dawnBlue)
        assertThat(writes).isEmpty()

        settle()
        assertThat(writes).containsExactly("global.gua.chat_theme" to dawnBlueContent)
        assertThat(cache.defaultTheme).isEqualTo(dawnBlueContent)
    }

    @Test
    fun `clearing the default writes an empty theme`() = runTest {
        val writes = mutableListOf<Pair<String, String>>()
        val setting = createSetting(stored = { oceanTealContent }, writes = writes)
        setting.preload()
        runCurrent()

        assertThat(setting.set(ChatTheme.STOCK).isSuccess).isTrue()

        assertThat(writes).containsExactly("global.gua.chat_theme" to "{}")
        assertThat(setting.theme.value).isEqualTo(ChatTheme.STOCK)
    }

    @Test
    fun `a stale read does not undo a write until its echo arrives`() = runTest {
        val roomListService = FakeRoomListService()
        var stored: String? = oceanTealContent
        val setting = createSetting(roomListService = roomListService, stored = { stored })
        roomListService.postState(RoomListService.State.Running)
        setting.preload()
        runCurrent()

        launch { setting.set(dawnBlue) }
        runCurrent()
        roomListService.emitSyncUpdate()
        settle()
        assertThat(setting.theme.value).isEqualTo(dawnBlue)

        advanceTimeBy(CHAT_THEME_REFRESH_INTERVAL)
        roomListService.emitSyncUpdate()
        settle()
        assertThat(setting.theme.value).isEqualTo(dawnBlue)

        stored = dawnBlueContent
        advanceTimeBy(CHAT_THEME_REFRESH_INTERVAL)
        roomListService.emitSyncUpdate()
        settle()
        assertThat(setting.theme.value).isEqualTo(dawnBlue)

        stored = oceanTealContent
        advanceTimeBy(CHAT_THEME_REFRESH_INTERVAL)
        roomListService.emitSyncUpdate()
        settle()
        assertThat(setting.theme.value).isEqualTo(oceanTeal)
    }

    @Test
    fun `a restarted room list accepts a value other than the unechoed write`() = runTest {
        val roomListService = FakeRoomListService()
        var stored: String? = oceanTealContent
        val setting = createSetting(roomListService = roomListService, stored = { stored })
        roomListService.postState(RoomListService.State.Running)
        setting.preload()
        runCurrent()
        setting.set(dawnBlue)
        advanceTimeBy(CHAT_THEME_REFRESH_INTERVAL)

        roomListService.postState(RoomListService.State.Idle)
        runCurrent()
        advanceTimeBy(CHAT_THEME_REFRESH_INTERVAL)
        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(setting.theme.value).isEqualTo(oceanTeal)
    }

    @Test
    fun `a failed write puts the stored default back`() = runTest {
        val cache = InMemoryChatBackgroundCache()
        val setting = createSetting(cache = cache, stored = { oceanTealContent }, writeResult = { Result.failure(Exception("offline")) })
        setting.preload()
        runCurrent()

        val result = setting.set(dawnBlue)

        assertThat(result.isFailure).isTrue()
        assertThat(setting.theme.value).isEqualTo(oceanTeal)
        assertThat(cache.defaultTheme).isEqualTo(oceanTealContent)
    }

    @Test
    fun `a failed write does not undo a newer request`() = runTest {
        val writes = mutableListOf<Pair<String, String>>()
        val setting = createSetting(
            writes = writes,
            writeResult = { if (writes.size == 1) Result.failure(Exception("offline")) else Result.success(Unit) },
        )
        setting.preload()
        runCurrent()

        launch { setting.set(oceanTeal) }
        launch { setting.set(dawnBlue) }
        settle()

        assertThat(writes.map { it.second }).containsExactly(oceanTealContent, dawnBlueContent).inOrder()
        assertThat(setting.theme.value).isEqualTo(dawnBlue)
    }

    private fun TestScope.settle() {
        advanceTimeBy(10)
        runCurrent()
    }

    private fun TestScope.createSetting(
        cache: InMemoryChatBackgroundCache = InMemoryChatBackgroundCache(),
        roomListService: FakeRoomListService = FakeRoomListService(),
        stored: () -> String? = { null },
        readResult: () -> Result<String?> = { Result.success(stored()) },
        writes: MutableList<Pair<String, String>> = mutableListOf(),
        writeResult: () -> Result<Unit> = { Result.success(Unit) },
    ) = DefaultChatThemeSetting(
        matrixClient = FakeMatrixClient(
            roomListService = roomListService,
            getAccountDataLambda = { type ->
                assertThat(type).isEqualTo("global.gua.chat_theme")
                readResult()
            },
            setAccountDataLambda = { type, content ->
                writes += type to content
                writeResult()
            },
        ),
        cache = cache,
        sessionCoroutineScope = backgroundScope,
    )
}
