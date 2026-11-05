/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.flow.Flow

interface ChatBackgroundService {
    fun preload()

    /** The background last known for [roomId], without waiting: the default until the cache has loaded. */
    fun currentBackground(roomId: RoomId): ChatBackground

    /** The cached background first, then the server's, then every newer local change; the server is read again on each return to the foreground. */
    fun backgroundFlow(room: JoinedRoom): Flow<ChatBackground>

    /** Applies only if newer than the current background, so old events reached by scrolling back are ignored. */
    fun onTimelineEvent(roomId: RoomId, eventId: EventId, timestamp: Long, contentJson: String?)

    /** Applies [background] locally as soon as the server accepts it. A photo must be uploaded first. */
    suspend fun set(room: JoinedRoom, background: ChatBackground): Result<Unit>
}
