/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class, DelicateCoilApi::class)

package io.element.android.features.messages.impl.timeline.components.event

import android.animation.ValueAnimator
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ImageRequest
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.components.MessagesReactionButton
import io.element.android.features.messages.impl.timeline.components.MessagesReactionsButtonContent
import io.element.android.features.messages.impl.timeline.model.anAggregatedReaction
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemStickerContent
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.contentvalidation.NoopContentValidationState
import io.element.android.libraries.matrixmedia.api.LocalPlayGifsAutomatically
import io.element.android.libraries.matrixmedia.test.RecordingImageLoader
import io.element.android.libraries.matrixmedia.test.isFirstFrameOnly
import io.element.android.libraries.matrixmedia.test.withSystemAnimationsOff
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class TimelineAnimatedImagesTest : RobolectricTest() {
    private val recorder by lazy { RecordingImageLoader(RuntimeEnvironment.getApplication()) }

    private val aGif = aTimelineItemImageContent().copy(
        filename = "hello.gif",
        mediaSource = MediaSource("mxc://gua.global/gif"),
        mimeType = MimeTypes.Gif,
        isAnimated = true,
    )

    private val aSticker = aTimelineItemStickerContent().copy(
        mediaSource = MediaSource("mxc://gua.global/sticker"),
        mimeType = MimeTypes.WebP,
    )

    @Before
    fun setUp() {
        SingletonImageLoader.setUnsafe(recorder.imageLoader)
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `a GIF shows its first frame and the GIF badge when GIFs do not play automatically`() = runAndroidComposeUiTest {
        setTimelineContent(playGifsAutomatically = false) { GifView() }
        assertThat(awaitRequest().isFirstFrameOnly).isTrue()
        onNodeWithText(activity!!.getString(R.string.gua_gif_badge)).assertExists()
    }

    @Test
    fun `a GIF plays without the GIF badge when GIFs play automatically, even with the system animations off`() {
        withSystemAnimationsOff(RuntimeEnvironment.getApplication()) {
            assertThat(ValueAnimator.areAnimatorsEnabled()).isFalse()
            runAndroidComposeUiTest {
                setTimelineContent(playGifsAutomatically = true) { GifView() }
                assertThat(awaitRequest().isFirstFrameOnly).isFalse()
                onNodeWithText(activity!!.getString(R.string.gua_gif_badge)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `a sticker shows its first frame when GIFs do not play automatically`() = runAndroidComposeUiTest {
        setTimelineContent(playGifsAutomatically = false) { StickerView() }
        assertThat(awaitRequest().isFirstFrameOnly).isTrue()
    }

    @Test
    fun `a sticker plays when GIFs play automatically, even with the system animations off`() {
        withSystemAnimationsOff(RuntimeEnvironment.getApplication()) {
            runAndroidComposeUiTest {
                setTimelineContent(playGifsAutomatically = true) { StickerView() }
                assertThat(awaitRequest().isFirstFrameOnly).isFalse()
            }
        }
    }

    @Test
    fun `a gallery cell shows the first frame when GIFs do not play automatically`() = runAndroidComposeUiTest {
        setTimelineContent(playGifsAutomatically = false) { GalleryView() }
        assertThat(awaitRequest().isFirstFrameOnly).isTrue()
    }

    @Test
    fun `a gallery cell loads as before when GIFs play automatically`() = runAndroidComposeUiTest {
        setTimelineContent(playGifsAutomatically = true) { GalleryView() }
        assertThat(awaitRequest().isFirstFrameOnly).isFalse()
    }

    @Test
    fun `an image reaction shows the first frame when GIFs do not play automatically`() = runAndroidComposeUiTest {
        setTimelineContent(playGifsAutomatically = false) { ImageReactionView() }
        assertThat(awaitRequest().isFirstFrameOnly).isTrue()
    }

    @Test
    fun `an image reaction loads as before when GIFs play automatically`() = runAndroidComposeUiTest {
        setTimelineContent(playGifsAutomatically = true) { ImageReactionView() }
        assertThat(awaitRequest().isFirstFrameOnly).isFalse()
    }

    @Composable
    private fun GalleryView() {
        TimelineItemGalleryView(
            eventId = null,
            content = aTimelineItemGalleryContent(
                items = listOf(aGalleryItem(filename = "hello.gif", mediaSource = MediaSource("mxc://gua.global/gif"))),
            ),
            onGalleryItemClick = {},
            onLongClick = null,
        )
    }

    @Composable
    private fun ImageReactionView() {
        MessagesReactionButton(
            onClick = {},
            onLongClick = {},
            content = MessagesReactionsButtonContent.Reaction(anAggregatedReaction(key = "mxc://gua.global/reaction")),
        )
    }

    @Composable
    private fun GifView() {
        TimelineItemImageView(
            content = aGif,
            hideMediaContent = false,
            onContentClick = null,
            onLongClick = null,
            onShowContentClick = {},
            contentValidationState = NoopContentValidationState(),
        )
    }

    @Composable
    private fun StickerView() {
        TimelineItemStickerView(
            content = aSticker,
            hideMediaContent = false,
            onContentClick = null,
            onLongClick = null,
            onShowClick = {},
            contentValidationState = NoopContentValidationState(),
        )
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setTimelineContent(
        playGifsAutomatically: Boolean,
        content: @Composable () -> Unit,
    ) {
        setContent {
            CompositionLocalProvider(LocalPlayGifsAutomatically provides playGifsAutomatically, content = content)
        }
    }

    private fun AndroidComposeUiTest<ComponentActivity>.awaitRequest(): ImageRequest {
        waitUntil(timeoutMillis = 5_000) { recorder.requests.isNotEmpty() }
        return recorder.requests.single()
    }
}
