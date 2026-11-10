/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.components.event.aMediaStackEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.protection.ProtectionState
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionEvent
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionState
import io.element.android.features.messages.impl.timeline.protection.aTimelineProtectionState
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class MediaStackTimelineTest {
    private val positions = MediaStackPositions()
    private val failures = mutableListOf<TimelineItem.Event>()
    private val opened = mutableListOf<Int>()

    @Test
    fun `a tap opens the front card, unless it failed to send, then it shows that member's failure`() {
        val stackEvent = aMediaStackEvent(
            isMine = true,
            members = List(3) { aTimelineItemImageContent() },
            sendStates = listOf(null, LocalEventSendState.Failed.Unknown("error"), null),
        )
        val stack = stackEvent.content as TimelineItemMediaStackContent

        tap(stackEvent)
        positions.show(stack, 1)
        tap(stackEvent)

        assertThat(opened).containsExactly(0)
        assertThat(failures).containsExactly(stack.members[1].event)
    }

    @Test
    fun `the first tap on a stack whose media are hidden shows them all`() {
        val stackEvent = aMediaStackEvent(isMine = false, members = List(2) { aTimelineItemImageContent() })
        val shown = mutableListOf<TimelineProtectionEvent>()
        val hidden = aTimelineProtectionState(protectionState = ProtectionState.RenderOnly(persistentSetOf()), eventSink = { shown += it })

        tap(stackEvent, hidden)

        assertThat(shown).hasSize(2)
        assertThat(opened).isEmpty()
        assertThat(failures).isEmpty()
    }

    private fun tap(stackEvent: TimelineItem.Event, timelineProtectionState: TimelineProtectionState = aTimelineProtectionState()) {
        positions.onStackClick(
            stackEvent = stackEvent,
            stack = stackEvent.content as TimelineItemMediaStackContent,
            timelineProtectionState = timelineProtectionState,
            onSendFailureClick = { failures += it },
            openMember = { _, index -> opened += index },
        )
    }
}
