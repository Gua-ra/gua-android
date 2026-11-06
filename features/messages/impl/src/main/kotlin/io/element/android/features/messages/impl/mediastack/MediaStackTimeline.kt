/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.element.android.features.messages.impl.timeline.FocusRequestState
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionEvent
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionState
import kotlinx.collections.immutable.ImmutableList

internal fun TimelineProtectionState.hiddenMembers(stack: TimelineItemMediaStackContent): List<TimelineItem.Event> =
    stack.members.map { it.event }.filter { hideMediaContent(it.eventId, it.isMine) }

internal fun TimelineProtectionState.showMembers(stack: TimelineItemMediaStackContent) =
    hiddenMembers(stack).forEach { eventSink(TimelineProtectionEvent.ShowContent(it.eventId)) }

// The first tap on a stack whose media are hidden shows them all, the next ones open the front card.
internal fun MediaStackPositions.onStackClick(
    stackEvent: TimelineItem.Event,
    stack: TimelineItemMediaStackContent,
    timelineProtectionState: TimelineProtectionState,
    openMember: (TimelineItem.Event, Int) -> Unit,
) {
    if (timelineProtectionState.hiddenMembers(stack).isEmpty()) {
        openMember(stackEvent, frontIndex(stack))
    } else {
        timelineProtectionState.showMembers(stack)
    }
}

// A jump to a member brings it to the front once, so the stack can be paged while the focus stays.
@Composable
internal fun BringFocusedMemberToFrontEffect(
    focusRequestState: FocusRequestState,
    timelineItems: ImmutableList<TimelineItem>,
    positions: MediaStackPositions,
) {
    val latestTimelineItems by rememberUpdatedState(timelineItems)
    LaunchedEffect(focusRequestState) {
        val focus = focusRequestState as? FocusRequestState.Success ?: return@LaunchedEffect
        if (!focus.rendered) positions.showMember(latestTimelineItems, focus.eventId)
    }
}
