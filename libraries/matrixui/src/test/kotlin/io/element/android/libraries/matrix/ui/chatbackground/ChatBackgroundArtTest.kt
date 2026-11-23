/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.test.core.app.ApplicationProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.useExistingImageAsPlaceholder
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.SensorBuilder

@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatBackgroundArtTest : RobolectricTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a still is halved only while it still covers the frame`() {
        assertThat(wallpaperSampleSize(1290, 2796, 231, 410)).isEqualTo(4)
        assertThat(wallpaperSampleSize(1290, 2796, 645, 1398)).isEqualTo(2)
        assertThat(wallpaperSampleSize(1290, 2796, 646, 1398)).isEqualTo(1)
        assertThat(wallpaperSampleSize(1290, 2796, 1080, 2000)).isEqualTo(1)
        assertThat(wallpaperSampleSize(1290, 2796, 2048, 1000)).isEqualTo(1)
        assertThat(wallpaperSampleSize(0, 0, 231, 410)).isEqualTo(1)
    }

    @Test
    fun `in a preview every artwork's still decodes in place in both appearances, covering the frame`() {
        ChatArtwork.entries.flatMap { listOf(it.lightFile, it.darkFile) }.forEach { file ->
            val still = decodeWallpaperStill(context, file, widthPx = 231, heightPx = 410)
            assertWithMessage(file).that(still).isNotNull()
            assertWithMessage(file).that(still!!.width).isIn(231 until 1290)
            assertWithMessage(file).that(still.height).isIn(410 until 2796)
        }
    }

    @Test
    fun `a still that cannot be read decodes to nothing, so the fallback shows`() {
        assertThat(decodeWallpaperStill(context, "missing-light.webp", widthPx = 231, heightPx = 410)).isNull()
    }

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun `a new size or appearance keeps the previous still on screen while its own loads`() {
        assertThat(wallpaperStillRequest(context, ChatArtwork.Silk.darkFile, widthPx = 231, heightPx = 410).useExistingImageAsPlaceholder).isTrue()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `at rest the grain of a still overscanned for tilt lines up with the live view's`() = runAndroidComposeUiTest<ComponentActivity> {
        val tile = aGrainTile()
        val shift = 5
        val sensorManager = activity!!.getSystemService(SensorManager::class.java)
        val gyroscope = SensorBuilder.newBuilder().setType(Sensor.TYPE_GYROSCOPE).build()
        setContent {
            val view = LocalView.current
            val parallax = remember { WallpaperParallax(WallpaperTiltSensor(sensorManager, gyroscope, view), shift = shift) }
            val side = with(LocalDensity.current) { 40.toDp() }
            Row {
                Box(Modifier.size(side).testTag("live").wallpaperGrain(tile).background(Color.Gray))
                Box(
                    Modifier
                        .size(side)
                        .testTag("still")
                        .wallpaperParallax(parallax)
                        .wallpaperGrain(tile, tileOrigin = Offset(shift.toFloat(), shift.toFloat()))
                        .background(Color.Gray)
                )
                Box(Modifier.size(side).testTag("unaligned").wallpaperParallax(parallax).wallpaperGrain(tile).background(Color.Gray))
            }
        }
        val live = onNodeWithTag("live").captureToImage().asAndroidBitmap()
        assertThat(onNodeWithTag("still").captureToImage().asAndroidBitmap().sameAs(live)).isTrue()
        assertThat(onNodeWithTag("unaligned").captureToImage().asAndroidBitmap().sameAs(live)).isFalse()
    }

    @Test
    fun `the grain uses soft light from API 29, and overlay below where soft light would draw as a plain veil`() {
        assertThat(wallpaperGrainStyle(Build.VERSION_CODES.Q)).isEqualTo(WallpaperGrainStyle(BlendMode.Softlight, alpha = 0.08f))
        assertThat(wallpaperGrainStyle(Build.VERSION_CODES.P)).isEqualTo(WallpaperGrainStyle(BlendMode.Overlay, alpha = 0.04f))
    }
}

/** A tile whose period does not divide the parallax shift, so a misaligned grain shows. */
private fun aGrainTile(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888)
    for (x in 0 until 7) {
        for (y in 0 until 7) {
            bitmap.setPixel(x, y, if ((3 * x + y) % 7 == 0) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        }
    }
    return bitmap.asImageBitmap()
}
