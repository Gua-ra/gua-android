/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.groups

import io.element.android.features.messages.impl.mediastack.MediaBatch
import io.element.android.features.messages.impl.mediastack.PendingMediaBatches
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.TimelineItemReadReceipts
import io.element.android.features.messages.impl.timeline.model.TimelineItemThreadInfo
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContentWithAttachment
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRedactedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemReadMarkerModel
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import kotlinx.collections.immutable.toImmutableList

/**
 * Turns the consecutive photos and videos one sender sent as a batch into a single stack item.
 */
class MediaStackGrouper(
    private val roomId: RoomId,
    private val pendingMediaBatches: PendingMediaBatches,
) {
    /**
     * @param newestFirst the timeline items, the most recent first.
     * @param stackIds keys are the unique ids of stack members, values the id of their stack, kept from one call to the next.
     */
    fun group(newestFirst: List<TimelineItem>, stackIds: MutableMap<String, String>): List<TimelineItem> {
        val items = newestFirst.asReversed()
        val batches = items.map { it.candidateBatch() }
        val result = ArrayList<TimelineItem>(items.size)
        val usedStackIds = HashSet<String>()
        var aloneIndex = -1
        var start = 0
        while (start < items.size) {
            val batch = batches[start]
            if (batch == null || start == aloneIndex) {
                result.add(items[start++])
            } else {
                val run = findRun(items, batches, start, batch)
                val runEnd = run.lastMemberIndex + 1
                run.aloneIndex?.let { aloneIndex = it }
                result.addRun(items.subList(start, runEnd), batches.subList(start, runEnd), stackIds, usedStackIds)
                start = runEnd
            }
        }
        return result.asReversed()
    }

    private fun TimelineItem.candidateBatch(): MediaBatch? {
        val event = this as? TimelineItem.Event ?: return null
        return when {
            event.isImageOrVideo() -> event.mediaBatch?.also { if (event.isMine) pendingMediaBatches.onSent(roomId, it) } ?: event.pendingBatch()
            event.content is TimelineItemRedactedContent -> event.mediaBatch
            else -> null
        }
    }

    private fun TimelineItem.Event.pendingBatch(): MediaBatch? {
        if (!isMine || eventId != null) return null
        val transactionId = transactionId ?: return null
        val media = content as TimelineItemEventContentWithAttachment
        return pendingMediaBatches.batchFor(
            roomId = roomId,
            transactionId = transactionId,
            media = PendingMediaBatches.Media(filename = media.filename, fileSize = media.fileSize ?: 0, mimeType = media.mimeType),
        )
    }
}

private data class Run(val lastMemberIndex: Int, val aloneIndex: Int?)

private fun findRun(items: List<TimelineItem>, batches: List<MediaBatch?>, start: Int, batch: MediaBatch): Run {
    val sender = (items[start] as TimelineItem.Event).senderId
    val indexes = mutableSetOf(batch.index)
    var end = start
    for (next in start + 1 until items.size) {
        val item = items[next]
        val itemBatch = batches[next]
        if (itemBatch != null && itemBatch.id == batch.id && (item as TimelineItem.Event).senderId == sender) {
            when {
                itemBatch.index in indexes -> return Run(lastMemberIndex = end, aloneIndex = null)
                // A later member heading a thread is shown on its own, so its thread summary stays reachable.
                item.threadInfo is TimelineItemThreadInfo.ThreadRoot -> return Run(lastMemberIndex = end, aloneIndex = next)
                else -> {
                    indexes += itemBatch.index
                    end = next
                }
            }
        } else if (!item.isReadMarker() && !item.isUnknownRemovedMessageFrom(sender, itemBatch)) {
            return Run(lastMemberIndex = end, aloneIndex = null)
        }
    }
    return Run(lastMemberIndex = end, aloneIndex = null)
}

private fun TimelineItem.isReadMarker() = this is TimelineItem.Virtual && model is TimelineItemReadMarkerModel

private fun TimelineItem.isUnknownRemovedMessageFrom(sender: UserId, batch: MediaBatch?) =
    batch == null && this is TimelineItem.Event && content is TimelineItemRedactedContent && senderId == sender

private fun TimelineItem.Event.isImageOrVideo() = content is TimelineItemImageContent || content is TimelineItemVideoContent

private fun MutableList<TimelineItem>.addRun(
    run: List<TimelineItem>,
    batches: List<MediaBatch?>,
    stackIds: MutableMap<String, String>,
    usedStackIds: MutableSet<String>,
) {
    val media = run.zip(batches).mapNotNull { (item, batch) ->
        val event = item as? TimelineItem.Event
        if (event != null && batch != null && event.isImageOrVideo()) event to batch else null
    }
    addAll(run.filter { it.isReadMarker() })
    when (media.size) {
        // Every member was removed: the first removed message stands for the whole batch.
        0 -> add(run.first())
        1 -> add(media.single().first)
        else -> {
            val members = media.sortedBy { it.second.index }.map { it.first }
            add(
                mediaStack(
                    id = stackIds.stackIdFor(members, usedStackIds),
                    members = members,
                    oldest = media.first().first,
                    newest = media.last().first,
                )
            )
        }
    }
}

private fun MutableMap<String, String>.stackIdFor(members: List<TimelineItem.Event>, usedStackIds: MutableSet<String>): UniqueId {
    val knownId = members.firstNotNullOfOrNull { this[it.id.value] }?.takeUnless { it in usedStackIds }
    val id = knownId ?: generateSequence(1) { it + 1 }
        .map { attempt -> computeStackIdWith(members.first(), attempt) }
        .first { it !in usedStackIds }
    members.forEach { this[it.id.value] = id }
    usedStackIds += id
    return UniqueId(id)
}

internal fun computeStackIdWith(firstMember: TimelineItem.Event, attempt: Int = 1): String = if (attempt == 1) {
    "${firstMember.id}_stack"
} else {
    "${firstMember.id}_stack_$attempt"
}

private fun mediaStack(
    id: UniqueId,
    members: List<TimelineItem.Event>,
    oldest: TimelineItem.Event,
    newest: TimelineItem.Event,
): TimelineItem.Event {
    val continuesBelow = newest.groupPosition == TimelineItemGroupPosition.First || newest.groupPosition == TimelineItemGroupPosition.Middle
    val groupPosition = when {
        oldest.groupPosition.isNew() && continuesBelow -> TimelineItemGroupPosition.First
        oldest.groupPosition.isNew() -> TimelineItemGroupPosition.None
        continuesBelow -> TimelineItemGroupPosition.Middle
        else -> TimelineItemGroupPosition.Last
    }
    return members.first().copy(
        id = id,
        content = TimelineItemMediaStackContent(stackId = id, members = members.toImmutableList()),
        sentTimeMillis = newest.sentTimeMillis,
        sentTime = newest.sentTime,
        sentDate = newest.sentDate,
        isEditable = false,
        groupPosition = groupPosition,
        readReceiptState = TimelineItemReadReceipts(members.flatMap { it.readReceiptState.receipts }.toImmutableList()),
        localSendState = members.firstOrNull { it.localSendState is LocalEventSendState.Failed }?.localSendState
            ?: members.firstOrNull { it.localSendState is LocalEventSendState.Sending }?.localSendState
            ?: members.first().localSendState,
    )
}
