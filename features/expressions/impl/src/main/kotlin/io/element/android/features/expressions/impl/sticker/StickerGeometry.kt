/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import io.element.android.features.expressions.api.sticker.StickerSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Bounds in pixels; [right] and [bottom] are exclusive. */
internal data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** The layout of a sticker made from a picture: trimmed, then fitted into 512×512 with a 16 px margin. */
internal object StickerGeometry {
    const val CANVAS_SIZE = 512
    const val PADDING = 16
    const val SHRINK_FACTOR = 0.8
    const val MIN_SIZE = 96

    /** The smallest box holding every pixel that is not fully transparent, null when there is none. */
    fun opaqueBounds(width: Int, height: Int, argbPixels: IntArray): PixelBounds? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (argbPixels[y * width + x] ushr 24 != 0) {
                    left = min(left, x)
                    top = min(top, y)
                    right = max(right, x)
                    bottom = max(bottom, y)
                }
            }
        }
        return if (right < 0) null else PixelBounds(left, top, right + 1, bottom + 1)
    }

    /** The trimmed picture's size once scaled, up or down, to fill the canvas inside its margin. */
    fun fittedContentSize(width: Int, height: Int): StickerSize {
        val box = (CANVAS_SIZE - 2 * PADDING).toDouble()
        val scale = box / max(width, height)
        return StickerSize(
            width = (width * scale).roundToInt().coerceIn(1, box.toInt()),
            height = (height * scale).roundToInt().coerceIn(1, box.toInt()),
        )
    }

    fun canvasSize(content: StickerSize) = StickerSize(content.width + 2 * PADDING, content.height + 2 * PADDING)

    /** The next, smaller size to try when the file is too large, or null at the smallest size. */
    fun shrunk(size: StickerSize): StickerSize? {
        if (max(size.width, size.height) <= MIN_SIZE) return null
        return StickerSize(
            width = (size.width * SHRINK_FACTOR).roundToInt().coerceAtLeast(1),
            height = (size.height * SHRINK_FACTOR).roundToInt().coerceAtLeast(1),
        )
    }
}
