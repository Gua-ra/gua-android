/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
sealed interface MessageMenuAction {
    data class Timeline(val action: TimelineItemAction) : MessageMenuAction
    data object Select : MessageMenuAction
}

data class MessageMenuSections(
    val primary: ImmutableList<MessageMenuAction>,
    val more: ImmutableList<MessageMenuAction>,
    val destructive: ImmutableList<MessageMenuAction>,
) {
    val isEmpty: Boolean
        get() = primary.isEmpty() && more.isEmpty() && destructive.isEmpty()

    companion object {
        val Empty = MessageMenuSections(persistentListOf(), persistentListOf(), persistentListOf())

        fun from(actions: List<TimelineItemAction>): MessageMenuSections {
            val menuActions = buildList {
                actions.forEach { action ->
                    add(MessageMenuAction.Timeline(action))
                    if (action == TimelineItemAction.CopyText) {
                        add(MessageMenuAction.Select)
                    }
                }
            }

            // Without threads, a reply inside a thread only offers Reply in thread, and it takes the place of Reply.
            val repliesOnlyInThread = TimelineItemAction.Reply !in actions

            fun placementOf(action: MessageMenuAction): Placement? {
                return if (repliesOnlyInThread && action == MessageMenuAction.Timeline(TimelineItemAction.ReplyInThread)) {
                    Placement(Section.Primary, 1)
                } else {
                    action.placement
                }
            }

            fun section(section: Section): ImmutableList<MessageMenuAction> {
                return menuActions
                    .mapNotNull { action -> placementOf(action)?.takeIf { it.section == section }?.let { action to it.order } }
                    .sortedBy { it.second }
                    .map { it.first }
                    .toImmutableList()
            }

            return MessageMenuSections(
                primary = section(Section.Primary),
                more = section(Section.More),
                destructive = section(Section.Destructive),
            )
        }
    }
}

internal enum class Section { Primary, More, Destructive }

internal data class Placement(val section: Section, val order: Int)

internal val MessageMenuAction.placement: Placement?
    get() = when (this) {
        MessageMenuAction.Select -> Placement(Section.Primary, 3)
        is MessageMenuAction.Timeline -> when (action) {
            TimelineItemAction.ViewInTimeline -> Placement(Section.Primary, 0)
            TimelineItemAction.Reply -> Placement(Section.Primary, 1)
            TimelineItemAction.CopyText,
            TimelineItemAction.CopyCaption -> Placement(Section.Primary, 2)
            TimelineItemAction.Edit,
            TimelineItemAction.EditCaption,
            TimelineItemAction.AddCaption,
            TimelineItemAction.EditPoll -> Placement(Section.Primary, 4)
            TimelineItemAction.Forward -> Placement(Section.Primary, 5)
            TimelineItemAction.Save -> Placement(Section.Primary, 6)
            TimelineItemAction.ReplyInThread -> Placement(Section.More, 0)
            TimelineItemAction.Pin,
            TimelineItemAction.Unpin -> Placement(Section.More, 1)
            TimelineItemAction.CopyLink -> Placement(Section.More, 2)
            TimelineItemAction.EndPoll -> Placement(Section.More, 3)
            TimelineItemAction.RemoveCaption -> Placement(Section.More, 4)
            TimelineItemAction.ViewSource -> Placement(Section.More, 5)
            TimelineItemAction.ReportContent -> Placement(Section.More, 6)
            TimelineItemAction.Redact -> Placement(Section.Destructive, 0)
            // Only a media stack with a member that failed to send offers these, for all its failed members at once.
            TimelineItemAction.RetrySending -> Placement(Section.Primary, 7)
            TimelineItemAction.AbortSending -> Placement(Section.Destructive, 1)
        }
    }
