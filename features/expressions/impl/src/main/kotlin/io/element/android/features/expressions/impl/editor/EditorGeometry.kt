/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.editor

import kotlin.math.max

/** How the picture is framed: zoomed about the canvas centre, then moved by a fraction of the canvas side. */
data class EditorFrame(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    companion object {
        val Initial = EditorFrame(scale = 1f, offsetX = 0f, offsetY = 0f)
    }
}

data class FrameRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** The editor's square canvas; the preview and the 512 px output use the same layout at different sides. */
object EditorGeometry {
    const val CANVAS_SIZE = 512
    const val OUTLINE_WIDTH = 6
    const val TEXT_SIZE = 56f
    const val TEXT_STROKE_WIDTH = 8f
    const val TEXT_MARGIN = 24f
    const val MAX_TEXT_LENGTH = 40
    const val MIN_SCALE = 0.25f
    const val MAX_SCALE = 6f

    /** The picture fitted inside the canvas, then zoomed and moved by [frame]. */
    fun destination(sourceWidth: Int, sourceHeight: Int, canvasSide: Float, frame: EditorFrame): FrameRect {
        val fit = canvasSide / max(sourceWidth, sourceHeight)
        val width = sourceWidth * fit * frame.scale
        val height = sourceHeight * fit * frame.scale
        val centreX = canvasSide / 2 + frame.offsetX * canvasSide
        val centreY = canvasSide / 2 + frame.offsetY * canvasSide
        return FrameRect(centreX - width / 2, centreY - height / 2, centreX + width / 2, centreY + height / 2)
    }

    fun transformed(frame: EditorFrame, zoom: Float, panX: Float, panY: Float, canvasSide: Float): EditorFrame = EditorFrame(
        scale = (frame.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE),
        offsetX = (frame.offsetX + panX / canvasSide).coerceIn(-1f, 1f),
        offsetY = (frame.offsetY + panY / canvasSide).coerceIn(-1f, 1f),
    )

    /** The outline thickness in the picture's own pixels, so it is [OUTLINE_WIDTH] once fitted to the canvas. */
    fun outlineWidth(sourceWidth: Int, sourceHeight: Int): Float = OUTLINE_WIDTH * max(sourceWidth, sourceHeight).toFloat() / CANVAS_SIZE

    fun textSize(measuredWidthAtFullSize: Float, canvasSide: Float = CANVAS_SIZE.toFloat()): Float {
        val available = canvasSide - 2 * TEXT_MARGIN * canvasSide / CANVAS_SIZE
        val fullSize = TEXT_SIZE * canvasSide / CANVAS_SIZE
        return if (measuredWidthAtFullSize <= available) fullSize else fullSize * available / measuredWidthAtFullSize
    }
}
