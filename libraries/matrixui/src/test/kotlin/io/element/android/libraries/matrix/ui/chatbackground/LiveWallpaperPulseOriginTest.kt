/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.chatbackground.api.UnitPoint
import org.junit.Test

class LiveWallpaperPulseOriginTest {
    private val stillShaped = Size(645f, 1398f)
    private val phone = Size(1080f, 2050f)
    private val centre = UnitPoint(0.5f, 0.5f)

    @Test
    fun `on a canvas shaped like the still with nothing over it, u_origin is the shaders' own default spot`() {
        assertOrigin(stillShaped, UnitPoint(0.5f, 0f), visibleTop = null, visibleBottom = null, expected = Offset(535.35f, 157.974f))
    }

    @Test
    fun `the spot is taken on the chat above the composer, in the frame's pixels from its bottom`() {
        // The frame reaches 145.42 px past the canvas at the top and at the bottom, so its bottom is at 2195.42.
        assertOrigin(phone, centre, visibleTop = 0f, visibleBottom = 1900f, expected = Offset(896.4f, 2195.42f - 1685.3f))
        assertSpot(phone, centre, visibleTop = 0f, visibleBottom = 1900f, expected = Offset(896.4f, 1685.3f))
    }

    @Test
    fun `the spot rides up with the keyboard`() {
        val closed = spotOnCanvas(phone, centre, visibleTop = 0f, visibleBottom = 1900f)
        val open = spotOnCanvas(phone, centre, visibleTop = 0f, visibleBottom = 1150f)
        assertNear(closed, Offset(896.4f, 1685.3f))
        assertNear(open, Offset(896.4f, 1020.05f))
        assertOrigin(phone, centre, visibleTop = 0f, visibleBottom = 1150f, expected = Offset(896.4f, 2195.42f - 1020.05f))
    }

    @Test
    fun `a taller composer lifts the spot`() {
        assertSpot(phone, centre, visibleTop = 0f, visibleBottom = 1900f, expected = Offset(896.4f, 1685.3f))
        assertSpot(phone, centre, visibleTop = 0f, visibleBottom = 1720f, expected = Offset(896.4f, 1525.64f))
        // With the keyboard open as well.
        assertSpot(phone, centre, visibleTop = 0f, visibleBottom = 970f, expected = Offset(896.4f, 860.39f))
    }

    @Test
    fun `without a composer the chat ends at the canvas's bottom less the system bottom inset it reaches into`() {
        assertSpot(phone, centre, visibleTop = 0f, visibleBottom = null, expected = Offset(896.4f, 1818.35f))
        assertSpot(phone, centre, visibleTop = 0f, visibleBottom = null, bottomInset = 63f, expected = Offset(896.4f, 1762.469f))
    }

    @Test
    fun `in the theme preview the chat ends at the top of its controls`() {
        val preview = Size(1080f, 2116f)
        assertSpot(preview, centre, visibleTop = null, visibleBottom = 1620f, expected = Offset(896.4f, 1436.94f))
    }

    @Test
    fun `on a canvas shaped like the still, the spot stays in the chat above the keyboard`() {
        val frame = liveWallpaperFrame(stillShaped, UnitPoint(0.5f, 0f))
        val origin = liveWallpaperPulseOrigin(stillShaped, frame, visibleTop = 0f, visibleBottom = 700f, bottomInset = 0f)
        assertNear(origin, Offset(535.35f, 1398f - 620.9f))
        assertInsideChat(frame.left + origin.x, frame.bottom - origin.y, stillShaped, top = 0f, bottom = 700f)
    }

    @Test
    fun `a chat area reported past the canvas only counts where the canvas is`() {
        // Starting under the top bar, above the canvas.
        assertSpot(phone, centre, visibleTop = -150f, visibleBottom = 1900f, expected = Offset(896.4f, 1685.3f))
        // Reaching below the canvas.
        assertSpot(phone, centre, visibleTop = 0f, visibleBottom = 2200f, expected = Offset(896.4f, 1818.35f))
        // Starting below the canvas's top.
        assertSpot(phone, centre, visibleTop = 100f, visibleBottom = 1900f, expected = Offset(896.4f, 1696.6f))
    }

    @Test
    fun `for every live artwork the spot is on its frame and in the chat, with the keyboard closed and open`() {
        ChatArtwork.entries.filter { it.isLive }.forEach { artwork ->
            listOf(1900f, 1150f).forEach { composerTop ->
                val frame = liveWallpaperFrame(phone, artwork.anchor)
                val origin = liveWallpaperPulseOrigin(phone, frame, visibleTop = 0f, visibleBottom = composerTop, bottomInset = 0f)
                val message = "${artwork.id} above $composerTop"
                assertWithMessage(message).that(origin.x).isIn(Range.closed(0f, frame.width))
                assertWithMessage(message).that(origin.y).isIn(Range.closed(0f, frame.height))
                assertInsideChat(frame.left + origin.x, frame.bottom - origin.y, phone, top = 0f, bottom = composerTop)
            }
        }
    }

    private fun spotOnCanvas(canvas: Size, anchor: UnitPoint, visibleTop: Float?, visibleBottom: Float?, bottomInset: Float = 0f): Offset {
        val frame = liveWallpaperFrame(canvas, anchor)
        val origin = liveWallpaperPulseOrigin(canvas, frame, visibleTop = visibleTop, visibleBottom = visibleBottom, bottomInset = bottomInset)
        return Offset(frame.left + origin.x, frame.bottom - origin.y)
    }

    private fun assertSpot(canvas: Size, anchor: UnitPoint, visibleTop: Float?, visibleBottom: Float?, bottomInset: Float = 0f, expected: Offset) {
        assertNear(spotOnCanvas(canvas, anchor, visibleTop, visibleBottom, bottomInset), expected)
    }

    private fun assertOrigin(canvas: Size, anchor: UnitPoint, visibleTop: Float?, visibleBottom: Float?, expected: Offset) {
        val origin = liveWallpaperPulseOrigin(canvas, liveWallpaperFrame(canvas, anchor), visibleTop, visibleBottom, bottomInset = 0f)
        assertNear(origin, expected)
    }

    private fun assertNear(actual: Offset, expected: Offset) {
        assertWithMessage("$actual").that(actual.x).isWithin(0.01f).of(expected.x)
        assertWithMessage("$actual").that(actual.y).isWithin(0.01f).of(expected.y)
    }

    private fun assertInsideChat(x: Float, y: Float, canvas: Size, top: Float, bottom: Float) {
        assertWithMessage("x $x").that(x).isIn(Range.closed(0f, canvas.width))
        assertWithMessage("y $y").that(y).isIn(Range.closed(top, bottom))
    }
}
