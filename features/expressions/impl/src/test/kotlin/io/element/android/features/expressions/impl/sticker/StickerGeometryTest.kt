/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.sticker.StickerSize
import org.junit.Test

class StickerGeometryTest {
    @Test
    fun `transparent edges are trimmed to the visible pixels`() {
        val width = 6
        val height = 5
        val pixels = IntArray(width * height)
        pixels[1 * width + 2] = 0x01FF0000
        pixels[3 * width + 4] = 0xFF00FF00.toInt()

        assertThat(StickerGeometry.opaqueBounds(width, height, pixels)).isEqualTo(PixelBounds(left = 2, top = 1, right = 5, bottom = 4))
    }

    @Test
    fun `a fully transparent picture has nothing to keep`() {
        assertThat(StickerGeometry.opaqueBounds(4, 4, IntArray(16) { 0x00FFFFFF })).isNull()
    }

    @Test
    fun `the picture is fitted inside a 16 px margin of a 512 canvas`() {
        val wide = StickerGeometry.fittedContentSize(width = 1000, height = 500)
        assertThat(wide).isEqualTo(StickerSize(width = 480, height = 240))
        assertThat(StickerGeometry.canvasSize(wide)).isEqualTo(StickerSize(width = 512, height = 272))

        val small = StickerGeometry.fittedContentSize(width = 40, height = 60)
        assertThat(small).isEqualTo(StickerSize(width = 320, height = 480))
        assertThat(StickerGeometry.canvasSize(small)).isEqualTo(StickerSize(width = 352, height = 512))
    }

    @Test
    fun `a file that is too large shrinks step by step down to a floor`() {
        val sizes = generateSequence(StickerSize(512, 512)) { StickerGeometry.shrunk(it) }.toList()

        assertThat(sizes.first()).isEqualTo(StickerSize(512, 512))
        assertThat(sizes[1]).isEqualTo(StickerSize(410, 410))
        assertThat(sizes.last().width).isAtMost(StickerGeometry.MIN_SIZE)
        assertThat(sizes.zipWithNext().all { (a, b) -> b.width < a.width }).isTrue()
    }
}
