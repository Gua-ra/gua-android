/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CHAT_BACKGROUND_EVENT_TYPE
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import retrofit2.HttpException

/** Reads a chat's background state from the server, since the SDK can neither fetch nor reliably store this custom state. */
interface RoomStateClient {
    suspend fun chatBackground(roomId: RoomId): RoomStateResult
}

sealed interface RoomStateResult {
    /** The stored timestamp is 0 when the server returned the content without the event. */
    data class Found(val stored: StoredChatBackground) : RoomStateResult

    data object NotFound : RoomStateResult

    /** Any other failure, a 401 included: the session is never signed out from here. */
    data class Failed(val cause: Throwable) : RoomStateResult
}

@ContributesBinding(SessionScope::class)
class DefaultRoomStateClient(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val retrofitFactory: RetrofitFactory,
) : RoomStateClient {
    override suspend fun chatBackground(roomId: RoomId): RoomStateResult {
        val response = runCatchingExceptions {
            val session = sessionStore.getSession(matrixClient.sessionId.value) ?: error("No stored session")
            // Unlogged, since every request carries the access token.
            val api = retrofitFactory.createUnlogged(session.homeserverUrl).create(RoomStateApi::class.java)
            api.stateEvent(
                authorization = "Bearer ${session.accessToken}",
                roomId = roomId.value,
                eventType = CHAT_BACKGROUND_EVENT_TYPE,
                format = "event",
            )
        }
        return response.fold(
            onSuccess = { RoomStateResult.Found(it.toStoredChatBackground()) },
            onFailure = { if ((it as? HttpException)?.code() == HTTP_NOT_FOUND) RoomStateResult.NotFound else RoomStateResult.Failed(it) },
        )
    }

    private fun JsonObject.toStoredChatBackground(): StoredChatBackground {
        val content = get("content") as? JsonObject
        return if (content != null && (get("type") as? JsonPrimitive)?.isString == true) {
            StoredChatBackground(
                contentJson = content.toString(),
                eventId = (get("event_id") as? JsonPrimitive)?.takeIf { it.isString }?.content,
                timestamp = (get("origin_server_ts") as? JsonPrimitive)?.longOrNull ?: 0,
            )
        } else {
            StoredChatBackground(contentJson = toString(), eventId = null, timestamp = 0)
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
