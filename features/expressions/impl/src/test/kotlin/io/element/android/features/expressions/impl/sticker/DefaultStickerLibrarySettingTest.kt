/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.parseJsonObject
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerLibraryCodec
import io.element.android.features.expressions.api.sticker.StickerLimitReachedException
import io.element.android.features.expressions.impl.ACCOUNT_DATA_REFRESH_INTERVAL
import io.element.android.features.expressions.impl.InMemoryAccountDataCache
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStickerLibrarySettingTest {
    private val cat = StickerImage(url = "mxc://gua.global/cat", body = "Cat", info = null)
    private val dog = StickerImage(url = "mxc://gua.global/dog", body = "Dog", info = null)
    private val catFavourite = """{"favourites":[{"body":"Cat","url":"mxc://gua.global/cat"}],"v":1}"""
    private val dogFavourite = """{"favourites":[{"body":"Dog","url":"mxc://gua.global/dog"}],"v":1}"""

    @Test
    fun `the cached library shows before the store is read`() = runTest {
        val cache = InMemoryAccountDataCache(mapOf(StickerLibraryCodec.EVENT_TYPE to catFavourite))
        val setting = createSetting(cache = cache, readResult = { Result.failure(Exception("store")) })

        assertThat(setting.library.value.favourites).isEmpty()
        setting.preload()
        runCurrent()

        assertThat(setting.library.value.favourites).containsExactly(cat)
    }

    @Test
    fun `the stored library replaces the cached one at session start and is cached`() = runTest {
        val cache = InMemoryAccountDataCache(mapOf(StickerLibraryCodec.EVENT_TYPE to catFavourite))
        val setting = createSetting(cache = cache, stored = { dogFavourite })

        setting.preload()
        runCurrent()

        assertThat(setting.library.value.favourites).containsExactly(dog)
        assertThat(parseJsonObject(cache.entries[StickerLibraryCodec.EVENT_TYPE])).isEqualTo(parseJsonObject(dogFavourite))
    }

    @Test
    fun `an empty store keeps the cached library until a sync confirms there is none`() = runTest {
        val roomListService = FakeRoomListService()
        val cache = InMemoryAccountDataCache(mapOf(StickerLibraryCodec.EVENT_TYPE to catFavourite))
        val setting = createSetting(cache = cache, roomListService = roomListService)

        setting.preload()
        runCurrent()
        assertThat(setting.library.value.favourites).containsExactly(cat)

        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(setting.library.value.favourites).isEmpty()
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
        roomListService.emitSyncUpdate()
        runCurrent()
        assertThat(reads).isEqualTo(2)

        stored = catFavourite
        repeat(3) {
            roomListService.emitSyncUpdate()
            advanceTimeBy(1.seconds)
            runCurrent()
        }
        assertThat(reads).isEqualTo(2)

        advanceTimeBy(ACCOUNT_DATA_REFRESH_INTERVAL)
        runCurrent()
        assertThat(reads).isEqualTo(3)
        assertThat(setting.library.value.favourites).containsExactly(cat)
    }

    @Test
    fun `an edit shows at once, then is stored and cached`() = runTest {
        val cache = InMemoryAccountDataCache()
        val writes = mutableListOf<Pair<String, String>>()
        val setting = createSetting(cache = cache, writes = writes)
        setting.preload()
        runCurrent()

        launch { setting.favourite(cat) }
        runCurrent()
        assertThat(setting.library.value.favourites).containsExactly(cat)
        assertThat(writes).isEmpty()

        settle()
        assertThat(writes.map { it.first }).containsExactly(StickerLibraryCodec.EVENT_TYPE)
        assertThat(parseJsonObject(writes.single().second)).isEqualTo(parseJsonObject(catFavourite))
        assertThat(cache.entries[StickerLibraryCodec.EVENT_TYPE]).isEqualTo(writes.single().second)
    }

    @Test
    fun `edits build on each other and keep unknown keys`() = runTest {
        val writes = mutableListOf<Pair<String, String>>()
        val setting = createSetting(stored = { """{"future":true,"v":1}""" }, writes = writes, now = 1_762_905_600_000)
        setting.preload()
        runCurrent()

        setting.add(cat)
        setting.favourite(cat)

        val last = parseJsonObject(writes.last().second)
        assertThat(last.keys).containsExactly("future", "favourites", "images", "v")
        assertThat(setting.library.value.stickers.single().shortcode).isEqualTo("smhv8h6o0")
        assertThat(setting.library.value.favourites).containsExactly(cat)
    }

    @Test
    fun `adding to a full library is refused without a write`() = runTest {
        val images = (0 until StickerLibraryCodec.MAX_STICKERS).joinToString(",") { """"s$it":{"url":"mxc://gua.global/s$it"}""" }
        val writes = mutableListOf<Pair<String, String>>()
        val setting = createSetting(stored = { """{"images":{$images},"v":1}""" }, writes = writes)
        setting.preload()
        runCurrent()

        val result = setting.add(cat)

        assertThat(result.exceptionOrNull()).isInstanceOf(StickerLimitReachedException::class.java)
        assertThat(writes).isEmpty()
        assertThat(setting.library.value.stickers).hasSize(StickerLibraryCodec.MAX_STICKERS)
    }

    @Test
    fun `a stale read does not undo an edit until its echo arrives`() = runTest {
        val roomListService = FakeRoomListService()
        var stored: String? = catFavourite
        val setting = createSetting(roomListService = roomListService, stored = { stored })
        roomListService.postState(RoomListService.State.Running)
        setting.preload()
        runCurrent()

        launch { setting.unfavourite(cat.url) }
        runCurrent()
        roomListService.emitSyncUpdate()
        settle()
        assertThat(setting.library.value.favourites).isEmpty()

        advanceTimeBy(ACCOUNT_DATA_REFRESH_INTERVAL)
        roomListService.emitSyncUpdate()
        settle()
        assertThat(setting.library.value.favourites).isEmpty()

        stored = """{"favourites":[],"v":1}"""
        advanceTimeBy(ACCOUNT_DATA_REFRESH_INTERVAL)
        roomListService.emitSyncUpdate()
        settle()
        assertThat(setting.library.value.favourites).isEmpty()

        stored = dogFavourite
        advanceTimeBy(ACCOUNT_DATA_REFRESH_INTERVAL)
        roomListService.emitSyncUpdate()
        settle()
        assertThat(setting.library.value.favourites).containsExactly(dog)
    }

    @Test
    fun `a restarted room list accepts a value other than the unechoed edit`() = runTest {
        val roomListService = FakeRoomListService()
        val setting = createSetting(roomListService = roomListService, stored = { catFavourite })
        roomListService.postState(RoomListService.State.Running)
        setting.preload()
        runCurrent()
        setting.unfavourite(cat.url)
        advanceTimeBy(ACCOUNT_DATA_REFRESH_INTERVAL)

        roomListService.postState(RoomListService.State.Idle)
        runCurrent()
        advanceTimeBy(ACCOUNT_DATA_REFRESH_INTERVAL)
        roomListService.postState(RoomListService.State.Running)
        settle()

        assertThat(setting.library.value.favourites).containsExactly(cat)
    }

    @Test
    fun `a failed write puts the stored library back`() = runTest {
        val cache = InMemoryAccountDataCache()
        val setting = createSetting(cache = cache, stored = { catFavourite }, writeResult = { Result.failure(Exception("offline")) })
        setting.preload()
        runCurrent()

        val result = setting.favourite(dog)

        assertThat(result.isFailure).isTrue()
        assertThat(setting.library.value.favourites).containsExactly(cat)
        assertThat(cache.entries[StickerLibraryCodec.EVENT_TYPE]).isEqualTo(parseJsonObject(catFavourite).toString())
    }

    @Test
    fun `a failed write does not undo a newer edit`() = runTest {
        val writes = mutableListOf<Pair<String, String>>()
        val setting = createSetting(
            writes = writes,
            writeResult = { if (writes.size == 1) Result.failure(Exception("offline")) else Result.success(Unit) },
        )
        setting.preload()
        runCurrent()

        launch { setting.favourite(cat) }
        launch { setting.favourite(dog) }
        settle()

        assertThat(writes).hasSize(2)
        assertThat(setting.library.value.favourites).containsExactly(dog, cat).inOrder()
    }

    @Test
    fun `deleting a sticker writes it out of the favourites too`() = runTest {
        val writes = mutableListOf<Pair<String, String>>()
        val stored = """{"favourites":[{"url":"mxc://gua.global/cat"}],"images":{"a":{"url":"mxc://gua.global/cat"}},"v":1}"""
        val setting = createSetting(stored = { stored }, writes = writes)
        setting.preload()
        runCurrent()

        setting.delete("a")

        assertThat(parseJsonObject(writes.single().second)).isEqualTo(parseJsonObject("""{"favourites":[],"images":{},"v":1}"""))
    }

    private fun TestScope.settle() {
        advanceTimeBy(10)
        runCurrent()
    }

    private fun TestScope.createSetting(
        cache: InMemoryAccountDataCache = InMemoryAccountDataCache(),
        roomListService: FakeRoomListService = FakeRoomListService(),
        stored: () -> String? = { null },
        readResult: () -> Result<String?> = { Result.success(stored()) },
        writes: MutableList<Pair<String, String>> = mutableListOf(),
        writeResult: () -> Result<Unit> = { Result.success(Unit) },
        now: Long = 0,
    ) = DefaultStickerLibrarySetting(
        matrixClient = FakeMatrixClient(
            roomListService = roomListService,
            getAccountDataLambda = { type ->
                assertThat(type).isEqualTo(StickerLibraryCodec.EVENT_TYPE)
                readResult()
            },
            setAccountDataLambda = { type, content ->
                writes += type to content
                writeResult()
            },
        ),
        cache = cache,
        sessionCoroutineScope = backgroundScope,
        clock = FakeSystemClock(now),
    )
}
