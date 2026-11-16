/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
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
    fun `the send pulse starts at the spot of the visible chat area, in the frame's pixels from its bottom left`() {
        // The same shape as the still: the shaders' own default spot.
        assertOrigin(Size(645f, 1398f), UnitPoint(0.5f, 0f), Offset(535.35f, 157.974f))
        // Narrower than the still: the frame reaches past both sides.
        assertOrigin(Size(1000f, 2796f), UnitPoint(0.5f, 0.5f), Offset(975f, 315.948f))
        assertOrigin(Size(1000f, 2796f), UnitPoint(1f, 0f), Offset(1120f, 315.948f))
        // Wider than the still: the frame reaches past the top and the bottom.
        assertOrigin(Size(645f, 1000f), UnitPoint(0.5f, 0.08f), Offset(535.35f, 479.16f))
    }

    @Test
    fun `the send pulse spot is on the visible chat area for every live artwork`() {
        val view = Size(1080f, 2050f)
        ChatArtwork.entries.filter { it.isLive }.forEach { artwork ->
            val frame = liveWallpaperFrame(view, artwork.anchor)
            val origin = liveWallpaperPulseOrigin(view, frame)
            assertWithMessage(artwork.id).that(origin.x).isAtLeast(0f)
            assertWithMessage(artwork.id).that(origin.x).isAtMost(frame.width)
            assertWithMessage(artwork.id).that(origin.y).isAtLeast(0f)
            assertWithMessage(artwork.id).that(origin.y).isAtMost(frame.height)
            assertWithMessage(artwork.id).that(frame.left + origin.x).isWithin(0.01f).of(view.width * 0.83f)
            assertWithMessage(artwork.id).that(frame.bottom - origin.y).isWithin(0.01f).of(view.height - view.height * 0.113f)
        }
    }

    private fun assertOrigin(viewSize: Size, anchor: UnitPoint, expected: Offset) {
        val origin = liveWallpaperPulseOrigin(viewSize, liveWallpaperFrame(viewSize, anchor))
        assertWithMessage("$viewSize $anchor").that(origin.x).isWithin(0.01f).of(expected.x)
        assertWithMessage("$viewSize $anchor").that(origin.y).isWithin(0.01f).of(expected.y)
    }

    private fun assertFrame(actual: Rect, expected: Rect) {
        listOf(actual.left to expected.left, actual.top to expected.top, actual.right to expected.right, actual.bottom to expected.bottom)
            .forEach { (value, expectedValue) -> assertWithMessage("$actual").that(value).isWithin(0.01f).of(expectedValue) }
    }
}
