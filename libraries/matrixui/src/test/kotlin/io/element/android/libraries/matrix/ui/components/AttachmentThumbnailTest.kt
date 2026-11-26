/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class, DelicateCoilApi::class)

package io.element.android.libraries.matrix.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
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
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrixmedia.api.LocalPlayGifsAutomatically
import io.element.android.libraries.matrixmedia.test.RecordingImageLoader
import io.element.android.libraries.matrixmedia.test.isFirstFrameOnly
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class AttachmentThumbnailTest : RobolectricTest() {
    private val recorder by lazy { RecordingImageLoader(RuntimeEnvironment.getApplication()) }

    @Before
    fun setUp() {
        SingletonImageLoader.setUnsafe(recorder.imageLoader)
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `a reply preview shows the first frame when GIFs do not play automatically`() = runAndroidComposeUiTest {
        setThumbnail(playGifsAutomatically = false)
        assertThat(awaitRequest().isFirstFrameOnly).isTrue()
    }

    @Test
    fun `a reply preview loads as before when GIFs play automatically`() = runAndroidComposeUiTest {
        setThumbnail(playGifsAutomatically = true)
        assertThat(awaitRequest().isFirstFrameOnly).isFalse()
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setThumbnail(playGifsAutomatically: Boolean) {
        setContent {
            CompositionLocalProvider(LocalPlayGifsAutomatically provides playGifsAutomatically) {
                AttachmentThumbnail(
                    info = AttachmentThumbnailInfo(
                        type = AttachmentThumbnailType.Image,
                        thumbnailSource = MediaSource("mxc://gua.global/gif"),
                        textContent = "hello.gif",
                    ),
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }

    private fun AndroidComposeUiTest<ComponentActivity>.awaitRequest(): ImageRequest {
        waitUntil(timeoutMillis = 5_000) { recorder.requests.isNotEmpty() }
        return recorder.requests.single()
    }
}
