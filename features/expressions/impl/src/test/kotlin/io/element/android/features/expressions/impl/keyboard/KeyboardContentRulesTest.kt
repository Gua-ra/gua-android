/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.keyboard

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeyboardContentRulesTest {
    @Test
    fun `GIFs and WhatsApp stickers are recognised from any source, other images only from the keyboard`() {
        assertThat(KeyboardContentRules.route("image/gif", isFromInputMethod = false)).isEqualTo(KeyboardRoute.Gif)
        assertThat(KeyboardContentRules.route("image/webp.wasticker", isFromInputMethod = false)).isEqualTo(KeyboardRoute.Sticker)
        assertThat(KeyboardContentRules.route("image/png", isFromInputMethod = true)).isEqualTo(KeyboardRoute.StickerIfTransparent)
        assertThat(KeyboardContentRules.route("image/png", isFromInputMethod = false)).isEqualTo(KeyboardRoute.Unchanged)
        assertThat(KeyboardContentRules.route("video/mp4", isFromInputMethod = true)).isEqualTo(KeyboardRoute.Unchanged)
        assertThat(KeyboardContentRules.route(null, isFromInputMethod = true)).isEqualTo(KeyboardRoute.Unchanged)
    }

    @Test
    fun `a keyboard image is a sticker when it is small and partly transparent`() {
        assertThat(KeyboardContentRules.isSticker(ImageFacts(1024, 800, isAnimated = false, hasTransparency = true))).isTrue()
        assertThat(KeyboardContentRules.isSticker(ImageFacts(1025, 800, isAnimated = false, hasTransparency = true))).isFalse()
        assertThat(KeyboardContentRules.isSticker(ImageFacts(512, 512, isAnimated = false, hasTransparency = false))).isFalse()
    }

    @Test
    fun `a sticker is re-encoded when larger than 512 or 300 KB, unless it is animated`() {
        val small = ImageFacts(512, 400, isAnimated = false, hasTransparency = true)
        assertThat(KeyboardContentRules.needsReencoding(small, size = 300_000)).isFalse()
        assertThat(KeyboardContentRules.needsReencoding(small, size = 300_001)).isTrue()
        assertThat(KeyboardContentRules.needsReencoding(small.copy(width = 513), size = 1000)).isTrue()
        assertThat(KeyboardContentRules.needsReencoding(small.copy(width = 900, isAnimated = true), size = 500_000)).isFalse()
    }

    @Test
    fun `a WhatsApp sticker uploads as WebP`() {
        assertThat(KeyboardContentRules.uploadMimeType("image/webp.wasticker")).isEqualTo("image/webp")
        assertThat(KeyboardContentRules.uploadMimeType("image/png")).isEqualTo("image/png")
    }

    @Test
    fun `animation is read from the WebP header`() {
        val animated = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8X".toByteArray() + ByteArray(4) + byteArrayOf(0x12) + ByteArray(11)
        val still = animated.copyOf().also { it[20] = 0x10 }

        assertThat(ImageHeaders.isAnimatedWebp(animated)).isTrue()
        assertThat(ImageHeaders.isAnimatedWebp(still)).isFalse()
        assertThat(ImageHeaders.isAnimatedWebp("GIF89a".toByteArray())).isFalse()
    }
}
