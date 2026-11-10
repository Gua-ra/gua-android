/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.event.toMediaStackMember
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import kotlinx.collections.immutable.toImmutableList

class TimelineItemMediaStackContentPreviewParam : PreviewParameterProvider<TimelineItemMediaStackContent> {
    override val values: Sequence<TimelineItemMediaStackContent>
        get() = sequenceOf(
            aMediaStackEvent(isMine = false, members = List(4) { aTimelineItemImageContent() }),
            aMediaStackEvent(isMine = true, members = List(2) { aTimelineItemImageContent() }),
            aMediaStackEvent(isMine = true, members = listOf(aTimelineItemVideoContent()) + List(3) { aTimelineItemImageContent() }),
            aMediaStackEvent(
                isMine = true,
                members = List(4) { aTimelineItemImageContent() },
                sendStates = listOf(
                    LocalEventSendState.Sending.MediaWithProgress(index = 0, progress = 40, total = 100),
                    LocalEventSendState.Sending.Event,
                    LocalEventSendState.Sending.Event,
                    LocalEventSendState.Sending.Event,
                ),
            ),
            aMediaStackEvent(
                isMine = true,
                members = List(3) { aTimelineItemImageContent() },
                sendStates = listOf(LocalEventSendState.Failed.Unknown("error"), null, null),
            ),
            aMediaStackEvent(
                isMine = false,
                members = listOf(aTimelineItemImageContent(caption = "Beach day")) + List(2) { aTimelineItemImageContent() },
            ),
        ).map { it.content as TimelineItemMediaStackContent }
}

internal fun aMediaStackEvent(
    isMine: Boolean,
    members: List<TimelineItemEventContent>,
    sendStates: List<LocalEventSendState?> = members.map { null },
): TimelineItem.Event {
    val stackMembers = members.mapIndexedNotNull { index, content ->
        aTimelineItemEvent(
            eventId = EventId("\$member$index"),
            isMine = isMine,
            content = content,
            sendState = sendStates[index],
        ).copy(id = UniqueId("member$index")).toMediaStackMember()
    }
    val stackId = UniqueId("member0_stack")
    return stackMembers.first().event.copy(
        id = stackId,
        content = TimelineItemMediaStackContent(
            stackId = stackId,
            members = stackMembers.toImmutableList(),
            newest = stackMembers.last().event,
            newestEventId = stackMembers.last().event.eventId,
        ),
        localSendState = sendStates.firstOrNull { it is LocalEventSendState.Failed } ?: sendStates.firstOrNull { it is LocalEventSendState.Sending },
    )
}
