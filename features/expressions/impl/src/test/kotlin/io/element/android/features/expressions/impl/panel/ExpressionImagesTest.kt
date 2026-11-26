/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class, DelicateCoilApi::class)

package io.element.android.features.expressions.impl.panel

import android.animation.ValueAnimator
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ImageRequest
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrixmedia.api.LocalPlayGifsAutomatically
import io.element.android.libraries.matrixmedia.test.RecordingImageLoader
import io.element.android.libraries.matrixmedia.test.isFirstFrameOnly
import io.element.android.libraries.matrixmedia.test.withSystemAnimationsOff
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.After
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class ExpressionImagesTest : RobolectricTest() {
    private val recorder by lazy { RecordingImageLoader(RuntimeEnvironment.getApplication()) }

    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `a GIF in the grid shows its first frame when GIFs do not play automatically`() = runAndroidComposeUiTest {
        setImageContent(playGifsAutomatically = false) {
            GifImageView(aGifItem("hello").preview!!, recorder.imageLoader, contentDescription = null, modifier = Modifier.size(100.dp))
        }
        assertThat(awaitRequest().isFirstFrameOnly).isTrue()
    }

    @Test
    fun `a GIF in the grid plays when GIFs play automatically, even with the system animations off`() {
        withSystemAnimationsOff(RuntimeEnvironment.getApplication()) {
            assertThat(ValueAnimator.areAnimatorsEnabled()).isFalse()
            runAndroidComposeUiTest {
                setImageContent(playGifsAutomatically = true) {
                    GifImageView(aGifItem("hello").preview!!, recorder.imageLoader, contentDescription = null, modifier = Modifier.size(100.dp))
                }
                assertThat(awaitRequest().isFirstFrameOnly).isFalse()
            }
        }
    }

    @Test
    fun `the preview sheet plays the GIF when GIFs do not play automatically`() = runAndroidComposeUiTest {
        setImageContent(playGifsAutomatically = false) {
            GifPreviewContent(
                item = aGifItem("hello"),
                isFavourite = false,
                isSending = false,
                imageLoader = recorder.imageLoader,
                onToggleFavourite = {},
                onSend = {},
            )
        }
        assertThat(awaitRequest().isFirstFrameOnly).isFalse()
    }

    @Test
    fun `a sticker shows its first frame when GIFs do not play automatically`() = runAndroidComposeUiTest {
        SingletonImageLoader.setUnsafe(recorder.imageLoader)
        setImageContent(playGifsAutomatically = false) {
            StickerImageView(aStickerImage("party"), contentDescription = null, modifier = Modifier.size(100.dp))
        }
        assertThat(awaitRequest().isFirstFrameOnly).isTrue()
    }

    @Test
    fun `a sticker plays when GIFs play automatically, even with the system animations off`() {
        withSystemAnimationsOff(RuntimeEnvironment.getApplication()) {
            SingletonImageLoader.setUnsafe(recorder.imageLoader)
            runAndroidComposeUiTest {
                setImageContent(playGifsAutomatically = true) {
                    StickerImageView(aStickerImage("party"), contentDescription = null, modifier = Modifier.size(100.dp))
                }
                assertThat(awaitRequest().isFirstFrameOnly).isFalse()
            }
        }
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setImageContent(
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
