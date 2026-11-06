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
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId

/**
 * The member shown at the front of each stack of a timeline. It is kept by member rather than by position, so the front card stays the
 * same when older members load in.
 */
@Stable
class MediaStackPositions {
    private val frontMembers = mutableStateMapOf<UniqueId, UniqueId>()

    fun frontIndex(stack: TimelineItemMediaStackContent): Int {
        val frontMember = frontMembers[stack.stackId] ?: return 0
        return stack.members.indexOfFirst { it.id == frontMember }.coerceAtLeast(0)
    }

    fun show(stack: TimelineItemMediaStackContent, index: Int) {
        frontMembers[stack.stackId] = stack.members[index.mod(stack.members.size)].id
    }

    fun showMember(stack: TimelineItemMediaStackContent, eventId: EventId) {
        val index = stack.members.indexOfFirst { it.eventId == eventId }
        if (index >= 0) show(stack, index)
    }
}

@Composable
fun rememberMediaStackPositions(): MediaStackPositions = remember { MediaStackPositions() }

val LocalMediaStackPositions = staticCompositionLocalOf { MediaStackPositions() }
