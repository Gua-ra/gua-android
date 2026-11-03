/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatbackground

import io.element.android.features.messages.impl.timeline.aGroupedEvents
import io.element.android.features.messages.impl.timeline.aTimelineItemDaySeparator
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineItemReactions
import io.element.android.features.messages.impl.timeline.aTimelineRoomInfo
import io.element.android.features.messages.impl.timeline.components.receipt.aReadReceiptData
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.TimelineItemReadReceipts
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemChatBackgroundContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemLoadingIndicatorModel
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemReadMarkerModel
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemRoomBeginningModel
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemTypingNotificationModel
import io.element.android.features.messages.impl.typing.aTypingNotificationState
import io.element.android.features.messages.impl.typing.aTypingRoomMember
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.timeline.Timeline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal fun aChatBackgroundTimelineItems(): ImmutableList<TimelineItem> = persistentListOf(
    TimelineItem.Virtual(UniqueId("typing"), TimelineItemTypingNotificationModel),
    aTimelineItemEvent(
        isMine = true,
        content = aTimelineItemTextContent(body = "Looks great!"),
        readReceiptState = TimelineItemReadReceipts(persistentListOf(aReadReceiptData(0), aReadReceiptData(1), aReadReceiptData(2), aReadReceiptData(3))),
    ),
    TimelineItem.Virtual(UniqueId("read_marker"), TimelineItemReadMarkerModel),
    aTimelineItemEvent(
        content = aTimelineItemTextContent(body = "How does it look?"),
        groupPosition = TimelineItemGroupPosition.Last,
        timelineItemReactions = aTimelineItemReactions(count = 2),
    ),
    aTimelineItemEvent(
        content = aTimelineItemTextContent(body = "I picked a new background for us"),
        groupPosition = TimelineItemGroupPosition.First,
    ),
    aTimelineItemEvent(content = TimelineItemChatBackgroundContent("Alice changed the chat background")),
    aGroupedEvents(),
    aTimelineItemDaySeparator(),
    TimelineItem.Virtual(UniqueId("loading"), TimelineItemLoadingIndicatorModel(Timeline.PaginationDirection.BACKWARDS, 0)),
    TimelineItem.Virtual(UniqueId("beginning"), TimelineItemRoomBeginningModel),
)

internal fun aChatBackgroundTimelineRoomInfo() = aTimelineRoomInfo(
    typingNotificationState = aTypingNotificationState(typingMembers = listOf(aTypingRoomMember("Alice"))),
)
