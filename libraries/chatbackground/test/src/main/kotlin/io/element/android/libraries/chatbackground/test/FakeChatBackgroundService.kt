/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.test

import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeChatBackgroundService(
    initialBackground: ChatBackground = ChatBackground.None,
    private val setResult: (ChatBackground) -> Result<Unit> = { Result.success(Unit) },
) : ChatBackgroundService {
    val background = MutableStateFlow(initialBackground)
    val timelineEvents = mutableListOf<TimelineEvent>()
    val sentBackgrounds = mutableListOf<ChatBackground>()

    data class TimelineEvent(val roomId: RoomId, val eventId: EventId, val timestamp: Long, val contentJson: String?)

    override fun preload() = Unit

    override fun currentBackground(roomId: RoomId): ChatBackground = background.value

    override fun backgroundFlow(room: JoinedRoom): Flow<ChatBackground> = background

    override fun onTimelineEvent(roomId: RoomId, eventId: EventId, timestamp: Long, contentJson: String?) {
        timelineEvents += TimelineEvent(roomId, eventId, timestamp, contentJson)
    }

    override suspend fun set(room: JoinedRoom, background: ChatBackground): Result<Unit> {
        sentBackgrounds += background
        return setResult(background).onSuccess { this.background.value = background }
    }
}
