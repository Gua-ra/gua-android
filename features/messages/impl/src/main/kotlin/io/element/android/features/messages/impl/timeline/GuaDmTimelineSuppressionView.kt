/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.di.LocalTimelineItemPresenterFactories
import io.element.android.features.messages.impl.timeline.di.aFakeTimelineItemPresenterFactories
import io.element.android.features.messages.impl.timeline.groups.TimelineItemGrouper
import io.element.android.features.messages.impl.timeline.groups.isDirectOneToOneRoomChangeEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemProfileChangeContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRoomMembershipContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateEventContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.protection.aTimelineProtectionState
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * GUA FORK Stage 4 verification.
 *
 * 1:1 direct chats are conversations, not "rooms", so membership/profile/state churn
 * (joined/left/invited, display-name/avatar updates, name/topic/encryption changes) must
 * NOT appear in the timeline — and therefore must never be collapsed into a "N room changes"
 * summary either. At runtime this is enforced in [io.element.android.features.messages.impl
 * .timeline.factories.event.TimelineItemEventFactory], which drops those events before they
 * reach the grouper. Mirrors the iOS `isDM` guards in `RoomTimelineItemFactory`.
 *
 * This preview exercises the SAME production classification ([isDirectOneToOneRoomChangeEvent])
 * and the SAME production grouper ([TimelineItemGrouper]) over an identical "raw" set of items:
 *
 *  - Left column ("Group room"): nothing is dropped, so the three room-change events collapse
 *    into a single "N room changes" grouped block above the visible message.
 *  - Right column ("1:1 chat"): the room-change events are suppressed, so only the message
 *    remains and there is no "room changes" block at all.
 *
 * If the suppression regressed, the 1:1 column would render the membership/state lines (or a
 * "room changes" group) and the recorded screenshot would diff.
 */
private val guaRoomChangeEvents = listOf(
    aTimelineItemEvent(
        isMine = false,
        content = TimelineItemRoomMembershipContent(body = "Alice joined the room"),
    ),
    aTimelineItemEvent(
        isMine = false,
        content = TimelineItemProfileChangeContent(body = "Alice changed their display name to Ana"),
    ),
    aTimelineItemEvent(
        isMine = false,
        content = TimelineItemStateEventContent(body = "Alice turned on end-to-end encryption"),
    ),
)

private val guaMessageEvent = aTimelineItemEvent(
    isMine = false,
    content = aTimelineItemTextContent(body = "Hey, how are you?"),
)

private fun guaGroupedTimeline(grouper: TimelineItemGrouper, isDirectOneToOneRoom: Boolean): ImmutableList<TimelineItem> {
    // Identical "raw" timeline for both cases: message + the three room-change events.
    val rawItems = guaRoomChangeEvents + guaMessageEvent
    val itemsForRoom = if (isDirectOneToOneRoom) {
        // Mirror the factory: drop the room-change events for a 1:1 DM.
        rawItems.filterNot { it.isDirectOneToOneRoomChangeEvent() }
    } else {
        rawItems
    }
    return grouper.group(itemsForRoom).toImmutableList()
}

@PreviewsDayNight
@Composable
internal fun GuaDmTimelineSuppressionPreview() = ElementPreview {
    ContentToPreview()
}

@Composable
private fun ContentToPreview() {
    CompositionLocalProvider(
        LocalTimelineItemPresenterFactories provides aFakeTimelineItemPresenterFactories(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Group room (room changes kept + grouped)",
                style = ElementTheme.typography.fontBodyMdMedium,
            )
            TimelineView(
                state = aTimelineState(
                    timelineItems = guaGroupedTimeline(TimelineItemGrouper(), isDirectOneToOneRoom = false),
                    timelineRoomInfo = aTimelineRoomInfo(isDm = false),
                ),
                timelineProtectionState = aTimelineProtectionState(),
                onUserDataClick = {},
                onLinkClick = {},
                onContentClick = {},
                onMessageLongClick = {},
                onSwipeToReply = {},
                onReactionClick = { _, _ -> },
                onReactionLongClick = { _, _ -> },
                onMoreReactionsClick = {},
                onReadReceiptClick = {},
                modifier = Modifier.height(220.dp),
                forceJumpToBottomVisibility = false,
            )
            Text(
                text = "1:1 chat (room changes suppressed)",
                style = ElementTheme.typography.fontBodyMdMedium,
            )
            TimelineView(
                state = aTimelineState(
                    timelineItems = guaGroupedTimeline(TimelineItemGrouper(), isDirectOneToOneRoom = true),
                    timelineRoomInfo = aTimelineRoomInfo(isDm = true),
                ),
                timelineProtectionState = aTimelineProtectionState(),
                onUserDataClick = {},
                onLinkClick = {},
                onContentClick = {},
                onMessageLongClick = {},
                onSwipeToReply = {},
                onReactionClick = { _, _ -> },
                onReactionLongClick = { _, _ -> },
                onMoreReactionsClick = {},
                onReadReceiptClick = {},
                modifier = Modifier.height(220.dp),
                forceJumpToBottomVisibility = false,
            )
        }
    }
}
