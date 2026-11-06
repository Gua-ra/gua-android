/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import android.os.Parcelable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContentWithAttachment
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.TransactionId
import io.element.android.libraries.matrix.api.timeline.ForwardExtraContents
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration.Companion.hours

// A local echo has no event JSON to read its batch from, so it is matched by the file it carries, then by its transaction id once bound.
@SingleIn(SessionScope::class)
@Inject
class PendingMediaBatches(
    private val systemClock: SystemClock,
) {
    @Parcelize
    data class Media(
        val filename: String,
        val fileSize: Long,
        val mimeType: String,
    ) : Parcelable

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

    // The first call binds the transaction id to the oldest unbound entry for the same file, so the same photo sent twice keeps each echo in its batch.
    @Synchronized
    fun bindBatch(roomId: RoomId, transactionId: TransactionId, media: Media): MediaBatch? {
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

// A forwarded echo carries the original's content, so it matches the key read from the original's timeline item.
fun TimelineItemEventContentWithAttachment.pendingMedia(): PendingMediaBatches.Media =
    PendingMediaBatches.Media(filename = filename, fileSize = fileSize ?: 0, mimeType = mimeType)

// Each chat forwarded to gets batches of its own, registered for that chat so its echoes stack before the server copies arrive.
fun PendingMediaBatches.forwardExtraContents(files: List<PendingMediaBatches.Media>): ForwardExtraContents = ForwardExtraContents { roomId ->
    val batches = MediaBatch.makeBatches(files.size)
    register(roomId, files.zip(batches).mapNotNull { (file, batch) -> batch?.let { file to it } })
    batches.map { it?.extraContentJson() }
}

fun MediaUploadInfo.pendingMedia(): PendingMediaBatches.Media = when (this) {
    is MediaUploadInfo.Image -> PendingMediaBatches.Media(file.name, imageInfo.size ?: 0, imageInfo.mimetype ?: MimeTypes.OctetStream)
    is MediaUploadInfo.Video -> PendingMediaBatches.Media(file.name, videoInfo.size ?: 0, videoInfo.mimetype ?: MimeTypes.OctetStream)
    else -> PendingMediaBatches.Media(file.name, file.length(), MimeTypes.OctetStream)
}
