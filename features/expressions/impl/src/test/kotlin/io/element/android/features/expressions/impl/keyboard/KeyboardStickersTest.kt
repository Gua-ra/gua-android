/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.keyboard

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.panel.ExpressionComposer
import io.element.android.features.expressions.api.panel.KeyboardContent
import io.element.android.features.expressions.api.sticker.StickerFile
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerRelation
import io.element.android.features.expressions.impl.sticker.StickerImageEncoder
import io.element.android.features.expressions.test.FakeGifSender
import io.element.android.features.expressions.test.FakeStickerSender
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardStickersTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val uri = Uri.parse("content://keyboard/1")

    @Test
    fun `content that is neither a sticker nor a GIF is left alone without being copied`() = runTest {
        var copies = 0
        val stickers = createKeyboardStickers(
            imageFiles = FakeImageFiles(copyResult = {
                copies++
                null
            }),
        )

        assertThat(stickers.take(KeyboardContent(uri, "image/jpeg", isFromInputMethod = false))).isNull()
        assertThat(copies).isEqualTo(0)
    }

    @Test
    fun `the mime type is looked up when the keyboard does not give one`() = runTest {
        val file = temporaryFolder.newFile()
        val stickers = createKeyboardStickers(imageFiles = FakeImageFiles(mimeTypeResult = { "image/gif" }, copyResult = { file }))

        assertThat(stickers.take(KeyboardContent(uri, null, isFromInputMethod = true))?.route).isEqualTo(KeyboardRoute.Gif)
    }

    @Test
    fun `a small transparent keyboard image is sent as it is as a sticker`() = runTest {
        val file = temporaryFolder.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val uploads = mutableListOf<StickerFile>()
        val sends = mutableListOf<StickerRelation>()
        val image = StickerImage(url = "mxc://gua.global/new", body = "Sticker", info = null)
        val composer = RecordingComposer()
        val stickers = createKeyboardStickers(
            imageFiles = FakeImageFiles(
                copyResult = { file },
                factsResult = { _, _ -> ImageFacts(400, 300, isAnimated = false, hasTransparency = true) },
            ),
            stickerSender = FakeStickerSender(
                uploadResult = {
                    uploads += it
                    Result.success(image)
                },
                sendResult = { _, relation ->
                    sends += relation
                    Result.success(AN_EVENT_ID)
                },
            ),
        )

        val received = stickers.take(KeyboardContent(uri, "image/png", isFromInputMethod = true))!!
        val outcome = stickers.send(received, composer)

        assertThat(outcome).isEqualTo(KeyboardStickers.Outcome.SentSticker(image))
        assertThat(uploads.single()).isEqualTo(StickerFile(byteArrayOf(1, 2, 3), "image/png", 400, 300, "Sticker", isAnimated = false))
        assertThat(sends).containsExactly(StickerRelation.None)
        assertThat(composer.sentCount).isEqualTo(1)
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `an opaque keyboard photo goes back to the old path`() = runTest {
        val file = temporaryFolder.newFile()
        val stickers = createKeyboardStickers(
            imageFiles = FakeImageFiles(
                copyResult = { file },
                factsResult = { _, _ -> ImageFacts(400, 300, isAnimated = false, hasTransparency = false) },
            ),
        )

        val outcome = stickers.send(stickers.take(KeyboardContent(uri, "image/jpeg", isFromInputMethod = true))!!, RecordingComposer())

        assertThat(outcome).isEqualTo(KeyboardStickers.Outcome.NotOurs)
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `a keyboard GIF is sent as a GIF with the reply`() = runTest {
        val file = temporaryFolder.newFile()
        val sends = mutableListOf<EventId?>()
        val composer = RecordingComposer(inReplyToEventId = AN_EVENT_ID)
        val stickers = createKeyboardStickers(
            imageFiles = FakeImageFiles(copyResult = { file }),
            gifSender = FakeGifSender(sendFileResult = { _, inReplyTo, _ ->
                sends += inReplyTo
                Result.success(Unit)
            }),
        )

        val outcome = stickers.send(stickers.take(KeyboardContent(uri, "image/gif", isFromInputMethod = false))!!, composer)

        assertThat(outcome).isEqualTo(KeyboardStickers.Outcome.SentGif)
        assertThat(sends).containsExactly(AN_EVENT_ID)
        assertThat(composer.sentCount).isEqualTo(1)
    }

    private class RecordingComposer(override val inReplyToEventId: EventId? = null) : ExpressionComposer {
        override val timelineMode: Timeline.Mode = Timeline.Mode.Live
        var sentCount = 0

        override suspend fun stickerRelation(): StickerRelation = inReplyToEventId?.let(StickerRelation::Reply) ?: StickerRelation.None

        override fun insertEmoji(emoji: String) = Unit

        override fun onSent() {
            sentCount++
        }
    }

    private fun TestScope.createKeyboardStickers(
        imageFiles: FakeImageFiles = FakeImageFiles(),
        stickerSender: FakeStickerSender = FakeStickerSender(),
        gifSender: FakeGifSender = FakeGifSender(),
    ) = KeyboardStickers(
        imageFiles = imageFiles,
        stickerSender = stickerSender,
        gifSender = gifSender,
        encoder = StickerImageEncoder(),
        dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
    )
}
