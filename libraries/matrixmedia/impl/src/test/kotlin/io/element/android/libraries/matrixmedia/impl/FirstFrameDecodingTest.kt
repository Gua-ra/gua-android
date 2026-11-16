/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrixmedia.impl

import coil3.ComponentRegistry
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.DataSource
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.svg.SvgDecoder
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.media.FakeMatrixMediaLoader
import io.element.android.libraries.matrixmedia.api.firstFrameOnly
import io.element.android.tests.testutils.robolectric.RobolectricTest
import okhttp3.OkHttpClient
import okio.Buffer
import okio.FileSystem
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class FirstFrameDecodingTest : RobolectricTest() {
    private val context get() = RuntimeEnvironment.getApplication()
    private val imageLoader by lazy {
        DefaultImageLoaderFactory(context) { OkHttpClient() }.newImageLoader(FakeMatrixMediaLoader())
    }

    @Test
    fun `a first-frame request decodes a GIF as a still`() {
        assertThat(decoderFor(GIF, firstFrameOnly = true)).isInstanceOf(BitmapFactoryDecoder::class.java)
        assertThat(decoderFor(GIF, firstFrameOnly = false)).isInstanceOf(AnimatedImageDecoder::class.java)
    }

    @Test
    fun `a first-frame request decodes an animated WebP as a still`() {
        assertThat(decoderFor(ANIMATED_WEBP, firstFrameOnly = true)).isInstanceOf(BitmapFactoryDecoder::class.java)
        assertThat(decoderFor(ANIMATED_WEBP, firstFrameOnly = false)).isInstanceOf(AnimatedImageDecoder::class.java)
    }

    @Test
    fun `a first-frame request leaves an SVG to the SVG decoder`() {
        assertThat(decoderFor(SVG, firstFrameOnly = true)).isInstanceOf(SvgDecoder::class.java)
    }

    /** Picks a decoder the way Coil does for [ImageRequest]: the request's own decoder factory first, then the loader's. */
    private fun decoderFor(bytes: ByteArray, firstFrameOnly: Boolean): Decoder? {
        val request = ImageRequest.Builder(context).data(bytes).apply { if (firstFrameOnly) firstFrameOnly() }.build()
        val registry = ComponentRegistry.Builder()
            .apply {
                request.decoderFactory?.let { add(it) }
                imageLoader.components.decoderFactories.forEach { add(it) }
            }
            .build()
        val result = SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.MEMORY,
        )
        return registry.newDecoder(result, Options(context), imageLoader)?.first
    }

    private companion object {
        val GIF = "GIF89a".toByteArray() + ByteArray(16)
        val SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/>""".toByteArray()

        // RIFF container with a VP8X chunk whose flags (byte 20) have the animation bit set.
        val ANIMATED_WEBP = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8X".toByteArray() + ByteArray(4) + byteArrayOf(0b10) + ByteArray(16)
    }
}
