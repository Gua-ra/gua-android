/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.item.event.EventTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.ImageMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.RedactedContent
import io.element.android.libraries.matrix.api.timeline.item.event.VideoMessageType

/**
 * The batch of each event of one timeline, parsed once per event. It outlives the redaction of an event, so a removed member of a
 * stack is still known as one.
 */
@Inject
class MediaBatchCache {
    private val batches = HashMap<EventId, MediaBatch?>()

    fun batchOf(eventId: EventId?, event: EventTimelineItem): MediaBatch? {
        eventId ?: return null
        return when (val content = event.content) {
            is MessageContent -> if (content.type is ImageMessageType || content.type is VideoMessageType) {
                if (batches.containsKey(eventId)) {
                    batches[eventId]
                } else {
                    MediaBatch.fromEventJson(
                        latestJson = event.latestJsonProvider(),
                        originalJson = { event.timelineItemDebugInfoProvider().originalJson },
                    ).also { batches[eventId] = it }
                }
            } else {
                null
            }
            is RedactedContent -> batches[eventId]
            else -> null
        }
    }
}
