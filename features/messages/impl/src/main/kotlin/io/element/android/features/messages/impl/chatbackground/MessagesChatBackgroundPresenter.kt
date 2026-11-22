/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatbackground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbackground.api.ChatBackgroundService
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.chatbackground.api.ChatThemeSetting
import io.element.android.libraries.chatbackground.api.effectiveChatTheme
import io.element.android.libraries.chatbackground.api.isPrivateChat
import io.element.android.libraries.core.coroutine.mapState
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaPreviewService
import io.element.android.libraries.matrix.api.media.isPreviewEnabled
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.ui.chatbackground.collectThemeAsState

fun interface MessagesChatBackgroundPresenter : Presenter<ChatTheme> {
    fun interface Factory {
        fun create(timelineController: TimelineController): MessagesChatBackgroundPresenter
    }
}

@AssistedInject
class DefaultMessagesChatBackgroundPresenter(
    @Assisted private val timelineController: TimelineController,
    private val room: JoinedRoom,
    private val chatBackgroundService: ChatBackgroundService,
    private val chatThemeSetting: ChatThemeSetting,
    private val mediaPreviewService: MediaPreviewService,
) : MessagesChatBackgroundPresenter {
    @AssistedFactory
    @ContributesBinding(RoomScope::class)
    interface Factory : MessagesChatBackgroundPresenter.Factory {
        override fun create(timelineController: TimelineController): DefaultMessagesChatBackgroundPresenter
    }

    private val showsBackground = when (timelineController.mainTimelineMode()) {
        Timeline.Mode.Live, is Timeline.Mode.FocusedOnEvent -> true
        Timeline.Mode.PinnedEvents, Timeline.Mode.Media, is Timeline.Mode.Thread -> false
    }

    @Composable
    override fun present(): ChatTheme {
        if (!showsBackground) return ChatTheme.STOCK
        LaunchedEffect(Unit) {
            reportTimelineChanges()
        }
        val chatTheme by chatBackgroundService.collectThemeAsState(room)
        val defaultTheme by chatThemeSetting.theme.collectAsState()
        val mediaPreviewValue by remember { mediaPreviewService.mediaPreviewConfigFlow.mapState { it.mediaPreviewValue } }.collectAsState()
        val joinRule by remember { room.roomInfoFlow.mapState { it.joinRule } }.collectAsState()
        return effectiveChatTheme(
            chatTheme = chatTheme,
            defaultTheme = defaultTheme,
            roomIsPrivate = joinRule.isPrivateChat(),
            mediaShown = mediaPreviewValue.isPreviewEnabled(joinRule),
        )
    }

    private suspend fun reportTimelineChanges() {
        var lastReported: EventId? = null
        timelineController.timelineItems().collect { items ->
            val latest = items.asSequence()
                .filterIsInstance<MatrixTimelineItem.Event>()
                .mapNotNull { item ->
                    val eventId = item.eventId ?: return@mapNotNull null
                    val background = (item.event.content as? StateContent)?.content as? OtherState.ChatBackground ?: return@mapNotNull null
                    Triple(eventId, item.event.timestamp, background.contentJson)
                }
                .maxByOrNull { (_, timestamp) -> timestamp }
                ?: return@collect
            val (eventId, timestamp, contentJson) = latest
            if (eventId == lastReported) return@collect
            lastReported = eventId
            chatBackgroundService.onTimelineEvent(room.roomId, eventId, timestamp, contentJson)
        }
    }
}
