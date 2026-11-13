/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorGeometryTest {
    @Test
    fun `the picture is fitted inside the canvas and centred`() {
        assertThat(EditorGeometry.destination(sourceWidth = 1000, sourceHeight = 500, canvasSide = 512f, frame = EditorFrame.Initial))
            .isEqualTo(FrameRect(left = 0f, top = 128f, right = 512f, bottom = 384f))
    }

    @Test
    fun `zooming scales about the centre and moving shifts by a fraction of the canvas`() {
        val frame = EditorFrame(scale = 2f, offsetX = 0.25f, offsetY = -0.125f)

        assertThat(EditorGeometry.destination(sourceWidth = 100, sourceHeight = 100, canvasSide = 512f, frame = frame))
            .isEqualTo(FrameRect(left = -128f, top = -320f, right = 896f, bottom = 704f))
    }

    @Test
    fun `the preview and the output agree at any canvas side`() {
        val frame = EditorFrame(scale = 1.5f, offsetX = 0.1f, offsetY = 0.2f)
        val preview = EditorGeometry.destination(800, 600, canvasSide = 1024f, frame = frame)
        val output = EditorGeometry.destination(800, 600, canvasSide = 512f, frame = frame)

        assertThat(preview.left / 2).isWithin(0.001f).of(output.left)
        assertThat(preview.width / 2).isWithin(0.001f).of(output.width)
    }

    @Test
    fun `gestures are measured in pixels of the preview and kept within bounds`() {
        val moved = EditorGeometry.transformed(EditorFrame.Initial, zoom = 2f, panX = 100f, panY = -50f, canvasSide = 400f)
        assertThat(moved).isEqualTo(EditorFrame(scale = 2f, offsetX = 0.25f, offsetY = -0.125f))

        val clamped = EditorGeometry.transformed(moved, zoom = 100f, panX = 10_000f, panY = 0f, canvasSide = 400f)
        assertThat(clamped.scale).isEqualTo(EditorGeometry.MAX_SCALE)
        assertThat(clamped.offsetX).isEqualTo(1f)
    }

    @Test
    fun `the outline is 6 px once the picture is fitted to 512`() {
        assertThat(EditorGeometry.outlineWidth(1024, 512)).isEqualTo(12f)
    }

    @Test
    fun `long text shrinks to stay inside the margins`() {
        assertThat(EditorGeometry.textSize(measuredWidthAtFullSize = 200f)).isEqualTo(EditorGeometry.TEXT_SIZE)
        assertThat(EditorGeometry.textSize(measuredWidthAtFullSize = 928f)).isEqualTo(EditorGeometry.TEXT_SIZE / 2)
    }
}
