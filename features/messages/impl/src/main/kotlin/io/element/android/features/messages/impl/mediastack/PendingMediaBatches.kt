/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.TransactionId
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlin.time.Duration.Companion.hours

/**
 * The batches of the media this session is sending. A local echo has no event JSON to read its batch from, so it is matched here by
 * the file it carries, then by its transaction id once bound.
 */
@SingleIn(SessionScope::class)
@Inject
class PendingMediaBatches(
    private val systemClock: SystemClock,
) {
    data class Media(
        val filename: String,
        val fileSize: Long,
        val mimeType: String,
    )

    private data class Entry(
        val roomId: RoomId,
        val media: Media,
        val batch: MediaBatch,
        val createdAt: Long,
        val transactionId: TransactionId? = null,
    )

    private val entries = mutableListOf<Entry>()

    @Synchronized
    fun register(roomId: RoomId, items: List<Pair<Media, MediaBatch>>) {
        val now = systemClock.epochMillis()
        items.forEach { (media, batch) -> entries += Entry(roomId, media, batch, now) }
    }

    /**
     * The batch of a local echo. The first call binds [transactionId] to the oldest unbound entry for the same file, so the same photo
     * sent twice keeps each echo in its own batch.
     */
    @Synchronized
    fun batchFor(roomId: RoomId, transactionId: TransactionId, media: Media): MediaBatch? {
        removeExpired()
        entries.firstOrNull { it.transactionId == transactionId }?.let { return it.batch }
        val index = entries.indexOfFirst { it.transactionId == null && it.roomId == roomId && it.media == media }
        if (index < 0) return null
        entries[index] = entries[index].copy(transactionId = transactionId)
        return entries[index].batch
    }

    @Synchronized
    fun onSent(roomId: RoomId, batch: MediaBatch) {
        entries.removeAll { it.roomId == roomId && it.batch == batch }
    }

    private fun removeExpired() {
        val oldestKept = systemClock.epochMillis() - EXPIRY.inWholeMilliseconds
        entries.removeAll { it.createdAt < oldestKept }
    }

    private companion object {
        val EXPIRY = 24.hours
    }
}

/** The file a local echo of this media will carry, as the timeline shows it. */
fun MediaUploadInfo.pendingMedia(): PendingMediaBatches.Media = when (this) {
    is MediaUploadInfo.Image -> PendingMediaBatches.Media(file.name, imageInfo.size ?: 0, imageInfo.mimetype ?: MimeTypes.OctetStream)
    is MediaUploadInfo.Video -> PendingMediaBatches.Media(file.name, videoInfo.size ?: 0, videoInfo.mimetype ?: MimeTypes.OctetStream)
    else -> PendingMediaBatches.Media(file.name, file.length(), MimeTypes.OctetStream)
}
