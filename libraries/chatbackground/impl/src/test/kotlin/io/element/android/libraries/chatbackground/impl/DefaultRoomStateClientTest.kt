/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class DefaultRoomStateClientTest {
    private val server = MockWebServer()
    private val client = aRoomStateClient(server)

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `the state is read as an event, with the session token and an empty state key`() = runTest {
        server.enqueue(MockResponse().setBody(AN_EVENT))

        val result = client.chatBackground(A_ROOM_ID)

        assertThat(result).isEqualTo(
            RoomStateResult.Found(StoredChatBackground(contentJson = """{"v":1,"kind":"preset","preset":"mint"}""", eventId = "\$event", timestamp = 1_000))
        )
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/_matrix/client/v3/rooms/!aRoomId:domain/state/global.gua.room.background/?format=event")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-1")
    }

    @Test
    fun `a room v12 id without a server name is sent unchanged`() = runTest {
        server.enqueue(MockResponse().setBody(AN_EVENT))

        client.chatBackground(RoomId("!31hneApxJ_1o-63DmFrpeqnkFfWppnzWso1JvH3ogLM"))

        assertThat(server.takeRequest().path)
            .isEqualTo("/_matrix/client/v3/rooms/!31hneApxJ_1o-63DmFrpeqnkFfWppnzWso1JvH3ogLM/state/global.gua.room.background/?format=event")
    }

    @Test
    fun `bare content is read with a zero timestamp`() = runTest {
        server.enqueue(MockResponse().setBody("""{"v":1,"kind":"preset","preset":"sky"}"""))

        assertThat(client.chatBackground(A_ROOM_ID)).isEqualTo(
            RoomStateResult.Found(StoredChatBackground(contentJson = """{"v":1,"kind":"preset","preset":"sky"}""", eventId = null, timestamp = 0))
        )
    }

    @Test
    fun `404 means no background`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"errcode":"M_NOT_FOUND"}"""))

        assertThat(client.chatBackground(A_ROOM_ID)).isEqualTo(RoomStateResult.NotFound)
    }

    @Test
    fun `server errors and 401 are failures`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"errcode":"M_UNKNOWN_TOKEN"}"""))

        assertThat(client.chatBackground(A_ROOM_ID)).isInstanceOf(RoomStateResult.Failed::class.java)
        assertThat(client.chatBackground(A_ROOM_ID)).isInstanceOf(RoomStateResult.Failed::class.java)
    }
}

internal const val AN_EVENT = """
    {"type":"global.gua.room.background","state_key":"","sender":"@bob:server.org","event_id":"${'$'}event","origin_server_ts":1000,
     "content":{"v":1,"kind":"preset","preset":"mint"}}
"""

internal fun aRoomStateClient(server: MockWebServer) = DefaultRoomStateClient(
    matrixClient = FakeMatrixClient(),
    sessionStore = InMemorySessionStore(
        initialList = listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = "token-1").copy(homeserverUrl = server.url("/").toString())),
    ),
    retrofitFactory = RetrofitFactory(okHttpClient = { OkHttpClient() }, json = { DefaultJsonProvider() }),
)
