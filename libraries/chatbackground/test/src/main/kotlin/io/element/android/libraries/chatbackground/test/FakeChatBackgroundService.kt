/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.test

import io.element.android.libraries.chatbackground.api.ChatBackgroundService
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeChatBackgroundService(
    initialTheme: ChatTheme = ChatTheme.STOCK,
    private val setResult: (ChatTheme) -> Result<Unit> = { Result.success(Unit) },
) : ChatBackgroundService {
    val theme = MutableStateFlow(initialTheme)
    val timelineEvents = mutableListOf<TimelineEvent>()
    val sentThemes = mutableListOf<ChatTheme>()

    data class TimelineEvent(val roomId: RoomId, val eventId: EventId, val timestamp: Long, val contentJson: String?)

    override fun preload() = Unit

    override fun currentTheme(roomId: RoomId): ChatTheme = theme.value

    override fun themeFlow(room: JoinedRoom): Flow<ChatTheme> = theme

    override fun onTimelineEvent(roomId: RoomId, eventId: EventId, timestamp: Long, contentJson: String?) {
        timelineEvents += TimelineEvent(roomId, eventId, timestamp, contentJson)
    }

    override suspend fun set(room: JoinedRoom, theme: ChatTheme): Result<Unit> {
        sentThemes += theme
        return setResult(theme).onSuccess { this.theme.value = theme }
    }
}
