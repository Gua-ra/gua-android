/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class, DelicateCoilApi::class)

package io.element.android.libraries.mediaviewer.impl.gallery.ui

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
import io.element.android.libraries.matrixmedia.api.LocalPlayGifsAutomatically
import io.element.android.libraries.matrixmedia.test.RecordingImageLoader
import io.element.android.libraries.matrixmedia.test.isFirstFrameOnly
import io.element.android.libraries.mediaviewer.impl.model.aMediaItemImage
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class ImageItemViewTest : RobolectricTest() {
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
    fun `a media browser cell shows the first frame when GIFs do not play automatically`() = runAndroidComposeUiTest {
        setImageItem(playGifsAutomatically = false)
        assertThat(awaitRequest().isFirstFrameOnly).isTrue()
    }

    @Test
    fun `a media browser cell loads as before when GIFs play automatically`() = runAndroidComposeUiTest {
        setImageItem(playGifsAutomatically = true)
        assertThat(awaitRequest().isFirstFrameOnly).isFalse()
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setImageItem(playGifsAutomatically: Boolean) {
        setContent {
            CompositionLocalProvider(LocalPlayGifsAutomatically provides playGifsAutomatically) {
                ImageItemView(
                    image = aMediaItemImage(mediaSourceUrl = "mxc://gua.global/gif"),
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.size(100.dp),
                )
            }
        }
    }

    private fun AndroidComposeUiTest<ComponentActivity>.awaitRequest(): ImageRequest {
        waitUntil(timeoutMillis = 5_000) { recorder.requests.isNotEmpty() }
        return recorder.requests.single()
    }
}
