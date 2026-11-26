/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import io.element.android.features.expressions.api.panel.ExpressionComposer
import io.element.android.features.expressions.api.sticker.StickerRelation
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.asEventId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.textcomposer.model.MessageComposerMode

internal class TimelineExpressionComposer(
    private val timelineController: TimelineController,
    private val composerState: () -> MessageComposerState,
) : ExpressionComposer {
    override val timelineMode: Timeline.Mode get() = timelineController.mainTimelineMode()

    override val inReplyToEventId: EventId? get() = (composerState().mode as? MessageComposerMode.Reply)?.eventId

    override suspend fun stickerRelation(): StickerRelation {
        val threadRoot = (timelineMode as? Timeline.Mode.Thread)?.threadRootId
        if (threadRoot != null) {
            var latestEventId: EventId? = null
            timelineController.invokeOnCurrentTimeline { latestEventId = getLatestEventId().getOrNull() }
            return StickerRelation.Thread(rootId = threadRoot, latestEventId = latestEventId ?: threadRoot.asEventId())
        }
        return inReplyToEventId?.let(StickerRelation::Reply) ?: StickerRelation.None
    }

    override fun insertEmoji(emoji: String) {
        composerState().eventSink(MessageComposerEvent.InsertText(emoji))
    }

    override fun onSent() {
        if (composerState().mode is MessageComposerMode.Reply) composerState().eventSink(MessageComposerEvent.CloseSpecialMode)
    }
}
