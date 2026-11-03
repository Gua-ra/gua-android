/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.messages.impl.actionlist.ActionListEvent
import io.element.android.features.messages.impl.actionlist.ActionListPresenter
import io.element.android.features.messages.impl.actionlist.ActionListState
import io.element.android.features.messages.impl.actionlist.model.TimelineItemActionPostProcessor
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.roomMembers
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.textcomposer.mentions.MentionSpanUpdater
import kotlinx.collections.immutable.persistentListOf

interface MessageMenuPresenter : Presenter<MessageMenuState> {
    fun interface Factory {
        fun create(timelineMode: Timeline.Mode): MessageMenuPresenter
    }
}

@AssistedInject
class DefaultMessageMenuPresenter(
    @Assisted timelineMode: Timeline.Mode,
    actionListPresenterFactory: ActionListPresenter.Factory,
    private val room: BaseRoom,
    private val mentionSpanUpdater: MentionSpanUpdater,
) : MessageMenuPresenter {
    @AssistedFactory
    @ContributesBinding(RoomScope::class)
    interface Factory : MessageMenuPresenter.Factory {
        override fun create(timelineMode: Timeline.Mode): DefaultMessageMenuPresenter
    }

    // A separate instance from the action sheet's, so the overlay and the sheet never show each other's target.
    private val actionListPresenter = actionListPresenterFactory.create(
        postProcessor = TimelineItemActionPostProcessor.Default,
        timelineMode = timelineMode,
    )

    @Composable
    override fun present(): MessageMenuState {
        val actionListState = actionListPresenter.present()
        val membersState by room.membersStateFlow.collectAsState()
        var request: Request? by remember { mutableStateOf(null) }
        var selectableText: String? by remember { mutableStateOf(null) }

        val target = remember(request, actionListState.target, membersState) {
            request?.let { buildTarget(it, actionListState.target, membersState.roomMembers().orEmpty()) } ?: MessageMenuState.Target.None
        }

        fun handleEvent(event: MessageMenuEvent) {
            when (event) {
                is MessageMenuEvent.Show -> {
                    request = Request(event.event.id, event.gesture)
                    actionListState.eventSink(ActionListEvent.ComputeForMessage(event.event, event.userEventPermissions))
                }
                MessageMenuEvent.Dismiss -> {
                    request = null
                    actionListState.eventSink(ActionListEvent.Clear)
                }
                is MessageMenuEvent.SelectText -> {
                    selectableText = (event.event.content as? TimelineItemTextBasedContent)?.selectableText(mentionSpanUpdater)
                }
                MessageMenuEvent.DismissTextSelection -> selectableText = null
            }
        }

        return MessageMenuState(
            target = target,
            selectableText = selectableText,
            eventSink = ::handleEvent,
        )
    }

    private fun buildTarget(
        request: Request,
        actionListTarget: ActionListState.Target,
        members: List<RoomMember>,
    ): MessageMenuState.Target {
        val computed = actionListTarget as? ActionListState.Target.Success
        if (computed == null || computed.event.id != request.eventId) return MessageMenuState.Target.None
        val event = computed.event
        val reactions = if (computed.displayEmojiReactions) MessageQuickReactions.build(computed.recentEmojis) else persistentListOf()
        val sections = when (request.gesture) {
            MessageMenuGesture.DoubleTap -> MessageMenuSections.Empty
            MessageMenuGesture.LongPress -> MessageMenuSections.from(computed.actions)
        }
        val reactionRows = when (request.gesture) {
            MessageMenuGesture.DoubleTap -> messageReactionRows(event.reactionsState.reactions, members)
            MessageMenuGesture.LongPress -> persistentListOf()
        }
        val hasSomethingToShow = when (request.gesture) {
            MessageMenuGesture.DoubleTap -> reactions.isNotEmpty()
            MessageMenuGesture.LongPress -> reactions.isNotEmpty() || !sections.isEmpty
        }
        if (!hasSomethingToShow) return MessageMenuState.Target.None
        return MessageMenuState.Target.Shown(
            event = event,
            gesture = request.gesture,
            reactions = reactions,
            highlightedReactions = event.reactionsState.highlightedKeys,
            sections = sections,
            reactionRows = reactionRows,
        )
    }

    private data class Request(
        val eventId: UniqueId,
        val gesture: MessageMenuGesture,
    )
}
