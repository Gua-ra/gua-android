/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifRenditions
import io.element.android.features.expressions.api.gif.GifSelection
import io.element.android.features.expressions.api.gif.GifSendException
import io.element.android.features.expressions.api.gif.GifSender
import io.element.android.features.expressions.impl.keyboard.ImageFiles
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.mapFailure
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.di.CacheDirectory
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.media.ThumbnailInfo
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID

/** Sends GIF files as they are: they never go through the media pre-processor. */
@ContributesBinding(RoomScope::class)
class DefaultGifSender(
    private val httpClient: GifHttpClient,
    private val mediaSenderFactory: MediaSenderFactory,
    private val blurhashEncoder: BlurhashEncoder,
    private val imageFiles: ImageFiles,
    @CacheDirectory private val cacheDir: File,
    private val dispatchers: CoroutineDispatchers,
) : GifSender {
    override suspend fun send(
        item: GifItem,
        caption: String?,
        inReplyToEventId: EventId?,
        timelineMode: Timeline.Mode,
    ): Result<Unit> {
        val selection = GifRenditions.forSending(item) ?: return Result.failure(GifSendException.NoRendition())
        return inSendDirectory { directory ->
            val media = withContext(dispatchers.io) { runCatchingExceptions { download(selection, directory) } }
                .getOrElse { return@inSendDirectory Result.failure(GifSendException.DownloadFailed(it)) }
            sendMedia(media, caption, inReplyToEventId, timelineMode)
        }
    }

    override suspend fun sendFile(file: File, inReplyToEventId: EventId?, timelineMode: Timeline.Mode): Result<Unit> = try {
        inSendDirectory { directory ->
            val media = withContext(dispatchers.io) { runCatchingExceptions { prepareFile(file, directory) } }
                .getOrElse { return@inSendDirectory Result.failure(GifSendException.UnreadableFile(it)) }
            sendMedia(media, caption = null, inReplyToEventId, timelineMode)
        }
    } finally {
        withContext(NonCancellable + dispatchers.io) { file.delete() }
    }

    private suspend fun sendMedia(media: MediaUploadInfo, caption: String?, inReplyToEventId: EventId?, timelineMode: Timeline.Mode): Result<Unit> =
        mediaSenderFactory.create(timelineMode)
            .sendPreProcessedMedia(
                mediaUploadInfo = media,
                caption = caption?.takeIf { it.isNotBlank() },
                formattedCaption = null,
                inReplyToEventId = inReplyToEventId,
            )
            .mapFailure { GifSendException.SendFailed(it) }

    private suspend fun inSendDirectory(block: suspend (File) -> Result<Unit>): Result<Unit> {
        val directory = File(cacheDir, "$SEND_DIRECTORY/${UUID.randomUUID()}")
        return try {
            block(directory)
        } finally {
            // The send queue has its own copy once the send is queued.
            withContext(NonCancellable + dispatchers.io) { directory.deleteRecursively() }
        }
    }

    private suspend fun download(selection: GifSelection, directory: File): MediaUploadInfo.Image {
        if (!directory.mkdirs()) throw IOException("Cannot create ${directory.name}")
        // Without a caption the SDK uses the file name as the body, so the file is named after the body.
        val file = File(directory, fileName(selection.body)).also { fetch(selection.file.url, it) }
        val stillFile = selection.still?.let { still ->
            File(directory, STILL_FILE_NAME).takeIf { target ->
                runCatchingExceptions { fetch(still.url, target) }
                    .onFailure { Timber.w(it, "Sending a GIF without its still") }
                    .isSuccess
            }
        }
        return MediaUploadInfo.Image(
            file = file,
            imageInfo = animatedImageInfo(file, selection.file.width, selection.file.height, MimeTypes.WebP, stillFile),
            thumbnailFile = stillFile,
        )
    }

    private fun prepareFile(source: File, directory: File): MediaUploadInfo.Image {
        if (!directory.mkdirs()) throw IOException("Cannot create ${directory.name}")
        val file = File(directory, GifRenditions.DEFAULT_BODY)
        source.copyTo(file)
        val facts = imageFiles.facts(file, MimeTypes.Gif) ?: throw IOException("Unreadable GIF")
        val stillFile = File(directory, STILL_FILE_NAME).takeIf { imageFiles.writeFirstFrame(file, it) }
        return MediaUploadInfo.Image(
            file = file,
            imageInfo = animatedImageInfo(file, facts.width, facts.height, MimeTypes.Gif, stillFile),
            thumbnailFile = stillFile,
        )
    }

    private fun animatedImageInfo(file: File, width: Int, height: Int, mimetype: String, stillFile: File?) = ImageInfo(
        height = height.toLong(),
        width = width.toLong(),
        mimetype = mimetype,
        size = file.length(),
        thumbnailInfo = stillFile?.let {
            ThumbnailInfo(height = height.toLong(), width = width.toLong(), mimetype = MimeTypes.Jpeg, size = it.length())
        },
        thumbnailSource = null,
        blurhash = stillFile?.let(blurhashEncoder::encode),
        isAnimated = true,
    )

    private suspend fun fetch(url: String, target: File) {
        httpClient.okHttpClient.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) throw IOException("The GIF host answered ${response.code}")
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_DOWNLOAD_SIZE) throw IOException("The GIF is larger than $MAX_DOWNLOAD_SIZE bytes")
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    companion object {
        private const val SEND_DIRECTORY = "gif-send"
        private const val STILL_FILE_NAME = "still.jpg"
        private const val MAX_DOWNLOAD_SIZE = 10_000_000L
        private const val MAX_FILE_NAME_LENGTH = 100
        private val unsafeFileNameCharacters = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

        internal fun fileName(body: String): String = body
            .replace(unsafeFileNameCharacters, " ")
            .trim()
            .take(MAX_FILE_NAME_LENGTH)
            .takeUnless { it.isEmpty() || it.all { char -> char == '.' } || it == STILL_FILE_NAME }
            ?: GifRenditions.DEFAULT_BODY
    }
}
