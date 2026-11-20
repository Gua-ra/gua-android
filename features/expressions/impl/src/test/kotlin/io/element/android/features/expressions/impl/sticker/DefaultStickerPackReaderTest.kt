/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
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

class DefaultStickerPackReaderTest {
    private val server = MockWebServer()
    private val reader = DefaultStickerPackReader(
        matrixClient = FakeMatrixClient(),
        sessionStore = InMemorySessionStore(
            initialList = listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = "token-1").copy(homeserverUrl = server.url("/").toString())),
        ),
        retrofitFactory = RetrofitFactory(okHttpClient = { OkHttpClient() }, json = { DefaultJsonProvider() }),
    )

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `packs of every state key are read from the whole room state`() = runTest {
        server.enqueue(MockResponse().setBody(A_ROOM_STATE))

        val packs = reader.packs(A_ROOM_ID, roomName = "Family", roomAvatarUrl = null).getOrThrow()

        assertThat(packs.map { it.eventType to it.stateKey }).containsExactly(
            "m.room.image_pack" to "",
            "m.room.image_pack" to "cats",
            "im.ponies.room_emotes" to "old",
        ).inOrder()
        assertThat(packs[1].displayName).isEqualTo("Cats")
        assertThat(packs[0].displayName).isEqualTo("Family")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/_matrix/client/v3/rooms/${A_ROOM_ID.value}/state")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-1")
    }

    @Test
    fun `a server error is a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThat(reader.packs(A_ROOM_ID, roomName = "Family", roomAvatarUrl = null).isFailure).isTrue()
    }

    private companion object {
        val A_ROOM_STATE = """
            [
              {"type":"m.room.name","state_key":"","content":{"name":"Family"}},
              {"type":"m.room.image_pack","state_key":"cats","content":{"images":{"wave":{"url":"mxc://gua.global/cat"}},"pack":{"display_name":"Cats"}}},
              {"type":"m.room.image_pack","state_key":"","content":{"images":{"dog":{"url":"mxc://gua.global/dog"}}}},
              {"type":"m.room.image_pack","state_key":"emotes","content":{"images":{"x":{"url":"mxc://gua.global/x"}},"pack":{"usage":["emoticon"]}}},
              {"type":"m.room.image_pack","state_key":"removed","content":{}},
              {"type":"im.ponies.room_emotes","state_key":"old","content":{"images":{"a":{"url":"mxc://gua.global/a"}}}}
            ]
        """.trimIndent()
    }
}
