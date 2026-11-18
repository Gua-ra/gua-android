/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import kotlinx.collections.immutable.ImmutableList

data class MessageMenuState(
    val target: Target,
    val selectableText: String?,
    val eventSink: (MessageMenuEvent) -> Unit,
) {
    @Immutable
    sealed interface Target {
        data object None : Target

        data class Shown(
            val event: TimelineItem.Event,
            val gesture: MessageMenuGesture,
            val reactions: ImmutableList<String>,
            val highlightedReactions: ImmutableList<String>,
            val sections: MessageMenuSections,
            val reactionRows: ImmutableList<MessageReactionRow>,
        ) : Target
    }
}

enum class MessageMenuGesture {
    DoubleTap,
    LongPress,
}
