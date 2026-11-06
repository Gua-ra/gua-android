/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.timeline.item.event.EventTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.ImageMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.RedactedContent
import io.element.android.libraries.matrix.api.timeline.item.event.VideoMessageType

// Kept for the life of the timeline, so a removed member of a stack is still known as one.
@Inject
class MediaBatchCache(
    private val room: BaseRoom,
    private val pendingMediaBatches: PendingMediaBatches,
) {
    private val batches = HashMap<EventId, MediaBatch?>()

    fun batchOf(event: EventTimelineItem): MediaBatch? {
        val eventId = event.eventId ?: return null
        return when {
            event.content is RedactedContent -> batches[eventId]
            !event.isImageOrVideo() -> null
            batches.containsKey(eventId) -> batches[eventId]
            else -> parse(event).also { batches[eventId] = it }
        }
    }

    private fun parse(event: EventTimelineItem): MediaBatch? {
        val batch = MediaBatch.fromEventJson(
            latestJson = event.latestJsonProvider(),
            originalJson = { event.timelineItemDebugInfoProvider().originalJson },
        )
        // The server copy of a photo this session sent carries its batch, so its local echo no longer needs matching.
        if (batch != null && event.isOwn) pendingMediaBatches.onSent(room.roomId, batch)
        return batch
    }
}

private fun EventTimelineItem.isImageOrVideo(): Boolean {
    val type = (content as? MessageContent)?.type
    return type is ImageMessageType || type is VideoMessageType
}
