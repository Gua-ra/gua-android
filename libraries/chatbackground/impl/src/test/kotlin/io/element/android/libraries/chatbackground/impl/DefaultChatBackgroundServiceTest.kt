/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.RoomStateEvent
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.services.appnavstate.test.FakeAppForegroundStateService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class DefaultChatBackgroundServiceTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `the cached background comes first, then the server's replaces it`() = runTest {
        server.enqueue(MockResponse().setBody(anEvent(preset = "sky", eventId = "\$server", timestamp = 10)))
        val cache = InMemoryChatBackgroundCache(mapOf(A_ROOM_ID to stored("mint", "\$cached", 2_000)))
        val service = createService(cache = cache)

        service.backgroundFlow(FakeJoinedRoom()).test {
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Sky))
        }
        assertThat(cache.entries[A_ROOM_ID]).isEqualTo(stored("sky", "\$server", 10))
    }

    @Test
    fun `404 means no background`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val service = createService(cache = InMemoryChatBackgroundCache(mapOf(A_ROOM_ID to stored("mint", "\$cached", 2_000))))

        service.backgroundFlow(FakeJoinedRoom()).test {
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
        }
    }

    @Test
    fun `a failed read keeps the cached background, and returning to the foreground reads again`() = runTest {
        var reads = 0
        val foreground = FakeAppForegroundStateService()
        val service = createService(
            roomStateClient = FakeRoomStateClient {
                reads++
                RoomStateResult.Failed(IllegalStateException("HTTP 500"))
            },
            cache = InMemoryChatBackgroundCache(mapOf(A_ROOM_ID to stored("mint", "\$cached", 2_000))),
            foreground = foreground,
        )

        service.backgroundFlow(FakeJoinedRoom()).test {
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))
            yield()
            foreground.givenIsInForeground(false)
            yield()
            foreground.givenIsInForeground(true)
            yield()
            expectNoEvents()
        }
        assertThat(reads).isEqualTo(2)
    }

    @Test
    fun `returning to the foreground replaces the background with the server's`() = runTest {
        server.enqueue(MockResponse().setBody(anEvent(preset = "mint", timestamp = 1)))
        server.enqueue(MockResponse().setBody(anEvent(preset = "sand", timestamp = 2)))
        val foreground = FakeAppForegroundStateService()
        val service = createService(foreground = foreground)

        service.backgroundFlow(FakeJoinedRoom()).test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))
            foreground.givenIsInForeground(false)
            yield()
            foreground.givenIsInForeground(true)
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Sand))
        }
    }

    @Test
    fun `a newer timeline event wins and an older one is ignored`() = runTest {
        val roomStateClient = FakeRoomStateClient(RoomStateResult.Found(stored("mint", "\$server", 1_000)))
        val service = createService(roomStateClient = roomStateClient)

        service.backgroundFlow(FakeJoinedRoom()).test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))
            service.onTimelineEvent(A_ROOM_ID, EventId("\$old"), 500, contentOf("sand"))
            service.onTimelineEvent(A_ROOM_ID, EventId("\$unreadable"), 3_000, null)
            service.onTimelineEvent(A_ROOM_ID, EventId("\$new"), 2_000, contentOf("ocean"))
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Ocean))
            service.onTimelineEvent(A_ROOM_ID, EventId("\$removed"), 2_500, "{}")
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
        }
    }

    @Test
    fun `a newer SDK snapshot wins, and empty or incomplete snapshots are ignored`() = runTest {
        val room = FakeJoinedRoom()
        val service = createService(roomStateClient = FakeRoomStateClient(RoomStateResult.Found(stored("mint", "\$server", 1_000))))

        service.backgroundFlow(room).test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))
            room.stateEvents.value = emptyList()
            testScheduler.runCurrent()
            room.stateEvents.value = listOf(aStateEvent("sky", eventId = null, timestamp = 5_000))
            testScheduler.runCurrent()
            room.stateEvents.value = listOf(aStateEvent("sand", eventId = "\$older", timestamp = 900))
            testScheduler.runCurrent()
            room.stateEvents.value = listOf(aStateEvent("blush", eventId = "\$newer", timestamp = 1_100))
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Blush))
        }
    }

    @Test
    fun `a sent background applies at once and its own event replaces the local stamp`() = runTest {
        val cache = InMemoryChatBackgroundCache()
        val room = FakeJoinedRoom(sendStateEventRawResult = { _, _, _ -> Result.success(EventId("\$sent")) })
        val service = createService(roomStateClient = FakeRoomStateClient(RoomStateResult.Found(stored("mint", "\$server", 1_000))), cache = cache)

        service.backgroundFlow(room).test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))

            assertThat(service.set(room, preset(ChatBackgroundPreset.Night)).isSuccess).isTrue()
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Night))
            assertThat(cache.entries[A_ROOM_ID]).isEqualTo(stored("night", "\$sent", 1_001))

            room.stateEvents.value = listOf(aStateEvent("mint", eventId = "\$server", timestamp = 1_000))
            testScheduler.runCurrent()
            service.onTimelineEvent(A_ROOM_ID, EventId("\$sent"), 5_000, contentOf("night"))
            service.onTimelineEvent(A_ROOM_ID, EventId("\$between"), 3_000, contentOf("sand"))
            testScheduler.runCurrent()
            expectNoEvents()
        }
        assertThat(cache.entries[A_ROOM_ID]).isEqualTo(stored("night", "\$sent", 5_000))
    }

    @Test
    fun `the content sent is the encoded background with an empty state key`() = runTest {
        val sent = mutableListOf<Triple<String, String, String>>()
        val room = FakeJoinedRoom(
            sendStateEventRawResult = { type, key, content ->
                sent += Triple(type, key, content)
                Result.success(EventId("\$sent"))
            },
        )
        val service = createService(roomStateClient = FakeRoomStateClient(RoomStateResult.NotFound))

        service.set(room, preset(ChatBackgroundPreset.Aurora))
        service.remove(room)

        assertThat(sent).containsExactly(
            Triple("global.gua.room.background", "", """{"v":1,"kind":"preset","preset":"aurora"}"""),
            Triple("global.gua.room.background", "", "{}"),
        ).inOrder()
    }

    @Test
    fun `a failed send changes nothing`() = runTest {
        val room = FakeJoinedRoom(sendStateEventRawResult = { _, _, _ -> Result.failure(IllegalStateException("403")) })
        val service = createService(roomStateClient = FakeRoomStateClient(RoomStateResult.Found(stored("mint", "\$server", 1_000))))

        service.backgroundFlow(room).test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))
            assertThat(service.set(room, preset(ChatBackgroundPreset.Night)).isFailure).isTrue()
            expectNoEvents()
        }
    }

    @Test
    fun `an older change seen while the server was being read gives way to the reply`() = runTest {
        val reply = CompletableDeferred<RoomStateResult>()
        val service = createService(roomStateClient = FakeRoomStateClient { reply.await() })

        service.backgroundFlow(FakeJoinedRoom()).test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            service.onTimelineEvent(A_ROOM_ID, EventId("\$stale"), 10, contentOf("sky"))
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Sky))
            reply.complete(RoomStateResult.Found(stored("mint", "\$server", 1_000)))
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))
        }
    }

    @Test
    fun `a newer change seen while the server was being read is kept over the reply`() = runTest {
        val reply = CompletableDeferred<RoomStateResult>()
        val service = createService(roomStateClient = FakeRoomStateClient { reply.await() })

        service.backgroundFlow(FakeJoinedRoom()).test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            service.onTimelineEvent(A_ROOM_ID, EventId("\$live"), 2_000, contentOf("sky"))
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Sky))
            reply.complete(RoomStateResult.Found(stored("mint", "\$server", 1_000)))
            testScheduler.runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `a stale SDK snapshot on the first open gives way to the server`() = runTest {
        val reply = CompletableDeferred<RoomStateResult>()
        val room = FakeJoinedRoom().apply { stateEvents.value = listOf(aStateEvent("sand", eventId = "\$old", timestamp = 10)) }
        val service = createService(roomStateClient = FakeRoomStateClient { reply.await() })

        service.backgroundFlow(room).test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Sand))
            reply.complete(RoomStateResult.Found(stored("ocean", "\$server", 1_000)))
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Ocean))
        }
    }

    @Test
    fun `a background sent while the server was being read is not undone by the reply`() = runTest {
        val reply = CompletableDeferred<RoomStateResult>()
        val room = FakeJoinedRoom(sendStateEventRawResult = { _, _, _ -> Result.success(EventId("\$sent")) })
        val service = createService(roomStateClient = FakeRoomStateClient { reply.await() })

        service.backgroundFlow(room).test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            assertThat(service.set(room, preset(ChatBackgroundPreset.Night)).isSuccess).isTrue()
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Night))
            reply.complete(RoomStateResult.Found(stored("mint", "\$server", 1_000)))
            testScheduler.runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `the current background is known without waiting once the cache has loaded`() = runTest {
        val cache = InMemoryChatBackgroundCache(mapOf(A_ROOM_ID to stored("mint", "\$cached", 2_000)))
        val service = createService(roomStateClient = FakeRoomStateClient(RoomStateResult.Found(stored("sky", "\$server", 3_000))), cache = cache)

        assertThat(service.currentBackground(A_ROOM_ID)).isEqualTo(ChatBackground.None)
        service.preload()
        testScheduler.runCurrent()
        assertThat(service.currentBackground(A_ROOM_ID)).isEqualTo(preset(ChatBackgroundPreset.Mint))

        service.backgroundFlow(FakeJoinedRoom()).test {
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Mint))
            assertThat(awaitItem()).isEqualTo(preset(ChatBackgroundPreset.Sky))
        }
        assertThat(service.currentBackground(A_ROOM_ID)).isEqualTo(preset(ChatBackgroundPreset.Sky))
    }

    private fun TestScope.createService(
        roomStateClient: RoomStateClient = aRoomStateClient(server),
        cache: ChatBackgroundCache = InMemoryChatBackgroundCache(),
        foreground: FakeAppForegroundStateService = FakeAppForegroundStateService(),
    ) = DefaultChatBackgroundService(
        roomStateClient = roomStateClient,
        cache = cache,
        appForegroundStateService = foreground,
        sessionCoroutineScope = backgroundScope,
    )
}

private class FakeRoomStateClient(private val result: suspend (RoomId) -> RoomStateResult) : RoomStateClient {
    constructor(result: RoomStateResult) : this({ result })

    override suspend fun chatBackground(roomId: RoomId): RoomStateResult = result(roomId)
}

private fun preset(preset: ChatBackgroundPreset) = ChatBackground.Preset(preset)

private fun contentOf(preset: String) = """{"v":1,"kind":"preset","preset":"$preset"}"""

private fun stored(preset: String, eventId: String?, timestamp: Long) = StoredChatBackground(contentOf(preset), eventId, timestamp)

private fun anEvent(preset: String, eventId: String = "\$event", timestamp: Long) =
    """{"type":"global.gua.room.background","state_key":"","event_id":"$eventId","origin_server_ts":$timestamp,"content":${contentOf(preset)}}"""

private fun aStateEvent(preset: String, eventId: String?, timestamp: Long?) = RoomStateEvent(
    stateKey = "",
    sender = A_USER_ID,
    contentJson = contentOf(preset),
    eventId = eventId?.let(::EventId),
    timestamp = timestamp,
)
