/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.chatbackground.api.UnitPoint
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveWallpaperShadersTest : RobolectricTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every live artwork's shader compiles once and takes every uniform`() {
        ChatArtwork.entries.filter { it.isLive }.forEach { artwork ->
            val shader = LiveWallpaperShaders.get(context, artwork)
            assertWithMessage(artwork.id).that(shader).isNotNull()
            LIVE_WALLPAPER_UNIFORMS.forEach { (name, size) ->
                shader!!.runtimeShader.setFloatUniform(name, FloatArray(size))
            }
            assertWithMessage(artwork.id).that(LiveWallpaperShaders.get(context, artwork)).isSameInstanceAs(shader)
        }
    }

    @Test
    fun `a static artwork has no shader`() {
        assertThat(LiveWallpaperShaders.get(context, ChatArtwork.PalmCoast)).isNull()
    }

    @Test
    fun `every live still has the frame the shader draws over`() {
        ChatArtwork.entries.filter { it.isLive }.flatMap { listOf(it.lightFile, it.darkFile) }.forEach { file ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("${ChatArtwork.ASSETS_FOLDER}/$file").use { BitmapFactory.decodeStream(it, null, bounds) }
            assertWithMessage(file).that(bounds.outWidth.toFloat() to bounds.outHeight.toFloat()).isEqualTo(LIVE_STILL_WIDTH to LIVE_STILL_HEIGHT)
        }
    }

    @Test
    fun `the shader's frame covers the view and is cropped around the anchor like the still`() {
        // Narrower than the still: the frame fits the height and the anchor picks which side is cut.
        assertFrame(liveWallpaperFrame(Size(1000f, 2796f), UnitPoint(0.5f, 0.5f)), Rect(-145f, 0f, 1145f, 2796f))
        assertFrame(liveWallpaperFrame(Size(1000f, 2796f), UnitPoint(1f, 0f)), Rect(-290f, 0f, 1000f, 2796f))
        // Wider than the still: the frame fits the width.
        assertFrame(liveWallpaperFrame(Size(645f, 1000f), UnitPoint(0.5f, 0.08f)), Rect(0f, -31.84f, 645f, 1366.16f))
        // The same shape as the still: nothing is cropped.
        assertFrame(liveWallpaperFrame(Size(645f, 1398f), UnitPoint(0.5f, 0f)), Rect(0f, 0f, 645f, 1398f))
    }

    @Test
    fun `the shader's frame lands where the still's crop does`() {
        val view = IntSize(1080, 2050)
        ChatArtwork.entries.filter { it.isLive }.forEach { artwork ->
            val frame = liveWallpaperFrame(view.toSize(), artwork.anchor)
            val stillSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt())
            val stillOffset = chatBackgroundAlignment(artwork.anchor).align(stillSize, view, LayoutDirection.Ltr)
            assertWithMessage(artwork.id).that(frame.left).isWithin(1f).of(stillOffset.x.toFloat())
            assertWithMessage(artwork.id).that(frame.top).isWithin(1f).of(stillOffset.y.toFloat())
        }
    }

    @Test
    fun `with tilt parallax the shader's frame lands where the overscanned still's crop does`() {
        val view = IntSize(1080, 2050)
        val shift = wallpaperParallaxShift(view.width.toDouble(), view.height.toDouble()).roundToInt()
        val overscanned = IntSize(view.width + 2 * shift, view.height + 2 * shift)
        ChatArtwork.entries.filter { it.isLive }.forEach { artwork ->
            val frame = liveWallpaperFrame(view.toSize(), artwork.anchor, shift.toFloat())
            val stillSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt())
            val stillOffset = chatBackgroundAlignment(artwork.anchor).align(stillSize, overscanned, LayoutDirection.Ltr)
            assertWithMessage(artwork.id).that(frame.left).isWithin(1f).of(stillOffset.x.toFloat() - shift)
            assertWithMessage(artwork.id).that(frame.top).isWithin(1f).of(stillOffset.y.toFloat() - shift)
            // It covers the view grown by the shift, so the still moved by a full tilt still covers the view.
            assertWithMessage(artwork.id).that(frame.left).isAtMost(0.01f - shift)
            assertWithMessage(artwork.id).that(frame.top).isAtMost(0.01f - shift)
            assertWithMessage(artwork.id).that(frame.right).isAtLeast(view.width + shift - 0.01f)
            assertWithMessage(artwork.id).that(frame.bottom).isAtLeast(view.height + shift - 0.01f)
        }
    }

    private fun assertFrame(actual: Rect, expected: Rect) {
        listOf(actual.left to expected.left, actual.top to expected.top, actual.right to expected.right, actual.bottom to expected.bottom)
            .forEach { (value, expectedValue) -> assertWithMessage("$actual").that(value).isWithin(0.01f).of(expectedValue) }
    }
}
