/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.gif.GifFile
import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifRendition
import io.element.android.features.expressions.api.gif.GifSendException
import io.element.android.features.expressions.api.gif.GifSize
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.media.ThumbnailInfo
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.mediaupload.api.MediaSender
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultGifSenderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `the hd file and its still are sent as an animated webp image, untouched`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(WEBP_BYTES)))
        server.enqueue(MockResponse().setBody(Buffer().write(JPEG_BYTES)))
        val mediaSender = RecordingMediaSender()
        val modes = mutableListOf<Timeline.Mode>()
        val sender = createSender(mediaSender = mediaSender, modes = modes)

        val result = sender.send(anItem(hdSize = 900_000), caption = "Morning!", inReplyToEventId = AN_EVENT_ID, timelineMode = Timeline.Mode.Live)

        assertThat(result.isSuccess).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/hd.webp")
        assertThat(server.takeRequest().path).isEqualTo("/hd.jpg")
        assertThat(modes).containsExactly(Timeline.Mode.Live)
        val sent = mediaSender.sent.single()
        assertThat(sent.caption).isEqualTo("Morning!")
        assertThat(sent.inReplyToEventId).isEqualTo(AN_EVENT_ID)
        assertThat(sent.fileName).isEqualTo("Hello")
        assertThat(sent.fileBytes).isEqualTo(WEBP_BYTES.toByteString())
        assertThat(sent.thumbnailBytes).isEqualTo(JPEG_BYTES.toByteString())
        assertThat(sent.imageInfo).isEqualTo(
            ImageInfo(
                height = 280,
                width = 498,
                mimetype = "image/webp",
                size = WEBP_BYTES.size.toLong(),
                thumbnailInfo = ThumbnailInfo(height = 280, width = 498, mimetype = "image/jpeg", size = JPEG_BYTES.size.toLong()),
                thumbnailSource = null,
                blurhash = "a-blurhash",
                isAnimated = true,
            )
        )
    }

    @Test
    fun `an hd file over 1,5 MB sends md, in the thread the composer is in`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(WEBP_BYTES)))
        server.enqueue(MockResponse().setBody(Buffer().write(JPEG_BYTES)))
        val mediaSender = RecordingMediaSender()
        val modes = mutableListOf<Timeline.Mode>()
        val sender = createSender(mediaSender = mediaSender, modes = modes)

        sender.send(anItem(hdSize = 1_500_001), caption = " ", inReplyToEventId = null, timelineMode = Timeline.Mode.Thread(A_THREAD_ID)).getOrThrow()

        assertThat(server.takeRequest().path).isEqualTo("/md.webp")
        assertThat(modes).containsExactly(Timeline.Mode.Thread(A_THREAD_ID))
        assertThat(mediaSender.sent.single().caption).isNull()
        assertThat(mediaSender.sent.single().imageInfo.width).isEqualTo(320)
    }

    @Test
    fun `a missing still sends the GIF without a thumbnail`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(WEBP_BYTES)))
        server.enqueue(MockResponse().setResponseCode(404))
        val mediaSender = RecordingMediaSender()

        createSender(mediaSender = mediaSender).send(anItem(hdSize = 900_000), caption = null, inReplyToEventId = null, timelineMode = Timeline.Mode.Live)

        val sent = mediaSender.sent.single()
        assertThat(sent.thumbnailBytes).isNull()
        assertThat(sent.imageInfo.thumbnailInfo).isNull()
        assertThat(sent.imageInfo.blurhash).isNull()
    }

    @Test
    fun `a failed download sends nothing and leaves no file behind`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val mediaSender = RecordingMediaSender()
        val cacheDir = temporaryFolder.newFolder()

        val result = createSender(mediaSender = mediaSender, cacheDir = cacheDir)
            .send(anItem(hdSize = 900_000), caption = null, inReplyToEventId = null, timelineMode = Timeline.Mode.Live)

        assertThat(result.exceptionOrNull()).isInstanceOf(GifSendException.DownloadFailed::class.java)
        assertThat(mediaSender.sent).isEmpty()
        assertThat(cacheDir.walk().filter { it.isFile }.toList()).isEmpty()
    }

    @Test
    fun `a failed send is a send failure and the files are removed`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(WEBP_BYTES)))
        server.enqueue(MockResponse().setBody(Buffer().write(JPEG_BYTES)))
        val cacheDir = temporaryFolder.newFolder()

        val result = createSender(mediaSender = RecordingMediaSender(result = Result.failure(Exception("offline"))), cacheDir = cacheDir)
            .send(anItem(hdSize = 900_000), caption = null, inReplyToEventId = null, timelineMode = Timeline.Mode.Live)

        assertThat(result.exceptionOrNull()).isInstanceOf(GifSendException.SendFailed::class.java)
        assertThat(cacheDir.walk().filter { it.isFile }.toList()).isEmpty()
    }

    @Test
    fun `an item without an animated rendition is refused`() = runTest {
        val result = createSender().send(
            GifItem(id = "x", title = "X", renditions = emptyMap()),
            caption = null,
            inReplyToEventId = null,
            timelineMode = Timeline.Mode.Live,
        )

        assertThat(result.exceptionOrNull()).isInstanceOf(GifSendException.NoRendition::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `the file name is a safe version of the body`() {
        assertThat(DefaultGifSender.fileName("Hello")).isEqualTo("Hello")
        assertThat(DefaultGifSender.fileName("  a/b\\c  ")).isEqualTo("a b c")
        assertThat(DefaultGifSender.fileName("..")).isEqualTo("GIF")
        assertThat(DefaultGifSender.fileName("x".repeat(300))).hasLength(100)
    }

    private fun anItem(hdSize: Long) = GifItem(
        id = "hello-42",
        title = "Hello",
        renditions = mapOf(
            GifSize.Hd to GifRendition(
                animated = GifFile(url = server.url("/hd.webp").toString(), width = 498, height = 280, size = hdSize),
                still = GifFile(url = server.url("/hd.jpg").toString(), width = 498, height = 280, size = 16_000),
            ),
            GifSize.Md to GifRendition(
                animated = GifFile(url = server.url("/md.webp").toString(), width = 320, height = 180, size = 300_000),
                still = GifFile(url = server.url("/md.jpg").toString(), width = 320, height = 180, size = 9000),
            ),
        ),
    )

    private fun createSender(
        mediaSender: RecordingMediaSender = RecordingMediaSender(),
        modes: MutableList<Timeline.Mode> = mutableListOf(),
        cacheDir: File = temporaryFolder.newFolder(),
    ) = DefaultGifSender(
        httpClient = GifHttpClient { OkHttpClient() },
        mediaSenderFactory = MediaSenderFactory { mode ->
            modes += mode
            mediaSender
        },
        blurhashEncoder = { "a-blurhash" },
        cacheDir = cacheDir,
        dispatchers = CoroutineDispatchers(io = Dispatchers.IO, computation = Dispatchers.Default, main = Dispatchers.Default),
    )

    private class RecordingMediaSender(private val result: Result<Unit> = Result.success(Unit)) : MediaSender {
        data class Sent(
            val imageInfo: ImageInfo,
            val fileName: String,
            val fileBytes: ByteString,
            val thumbnailBytes: ByteString?,
            val caption: String?,
            val inReplyToEventId: EventId?,
        )

        val sent = mutableListOf<Sent>()

        override suspend fun sendPreProcessedMedia(
            mediaUploadInfo: MediaUploadInfo,
            caption: String?,
            formattedCaption: String?,
            inReplyToEventId: EventId?,
        ): Result<Unit> {
            val image = mediaUploadInfo as MediaUploadInfo.Image
            sent += Sent(
                imageInfo = image.imageInfo,
                fileName = image.file.name,
                fileBytes = image.file.readBytes().toByteString(),
                thumbnailBytes = image.thumbnailFile?.readBytes()?.toByteString(),
                caption = caption,
                inReplyToEventId = inReplyToEventId,
            )
            return result
        }

        override suspend fun preProcessMedia(uri: Uri, mimeType: String, mediaOptimizationConfig: MediaOptimizationConfig) = error("Not used")

        override suspend fun sendMedia(
            uri: Uri,
            mimeType: String,
            caption: String?,
            formattedCaption: String?,
            inReplyToEventId: EventId?,
            mediaOptimizationConfig: MediaOptimizationConfig,
        ) = error("Not used")

        override suspend fun sendVoiceMessage(uri: Uri, mimeType: String, waveForm: List<Float>, inReplyToEventId: EventId?) = error("Not used")

        override suspend fun sendGallery(
            mediaUploadInfos: List<MediaUploadInfo>,
            caption: String?,
            formattedCaption: String?,
            inReplyToEventId: EventId?,
        ) = error("Not used")

        override fun cleanUp() = Unit
    }

    private companion object {
        val WEBP_BYTES = "RIFF....WEBPVP8X".toByteArray()
        val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    }
}
