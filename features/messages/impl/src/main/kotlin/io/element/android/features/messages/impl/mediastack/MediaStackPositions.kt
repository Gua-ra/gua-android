/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId

// Kept by member rather than by position, so the front card stays the same when older members load in.
@Stable
class MediaStackPositions {
    private val frontMembers = mutableStateMapOf<UniqueId, UniqueId>()

    fun frontIndex(stack: TimelineItemMediaStackContent): Int {
        val frontMember = frontMembers[stack.stackId] ?: return 0
        return stack.members.indexOfFirst { it.event.id == frontMember }.coerceAtLeast(0)
    }

    fun show(stack: TimelineItemMediaStackContent, index: Int) {
        frontMembers[stack.stackId] = stack.members[index.mod(stack.members.size)].event.id
    }

    fun showMember(timelineItems: List<TimelineItem>, eventId: EventId) {
        timelineItems.forEach { item ->
            val stack = (item as? TimelineItem.Event)?.content as? TimelineItemMediaStackContent ?: return@forEach
            val index = stack.members.indexOfFirst { it.event.eventId == eventId }
            if (index >= 0) return show(stack, index)
        }
    }
}

@Composable
fun rememberMediaStackPositions(): MediaStackPositions = remember { MediaStackPositions() }

val LocalMediaStackPositions = staticCompositionLocalOf { MediaStackPositions() }
