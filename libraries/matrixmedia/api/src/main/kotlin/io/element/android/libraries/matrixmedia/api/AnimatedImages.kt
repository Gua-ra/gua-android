/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrixmedia.api

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.gif.isAnimatedHeif
import coil3.gif.isAnimatedWebP
import coil3.gif.isGif
import coil3.request.ImageRequest
import coil3.request.Options

/**
 * The "Play GIFs automatically" setting: the one check for GIFs and animated stickers that play on their own,
 * whatever the system animation settings. A GIF the user opens plays either way.
 */
val LocalPlayGifsAutomatically = compositionLocalOf { true }

/** [data] as it is when it may play, otherwise a request for its first frame only. */
fun Context.animatedImageModel(data: Any, play: Boolean): Any = if (play) {
    data
} else {
    ImageRequest.Builder(this).data(data).firstFrameOnly().build()
}

/** Decodes only the first frame of an animated image, cached apart from the animated image. */
fun ImageRequest.Builder.firstFrameOnly(): ImageRequest.Builder = this
    .decoderFactory(FirstFrameDecoderFactory)
    .memoryCacheKeyExtra("frame", "first")

/**
 * Decodes a GIF, animated WebP or animated HEIF as a still of its first frame. It declines anything else, so an SVG or a
 * still image goes on to the image loader's own decoders as if no first frame had been asked for.
 * A single instance, so two first-frame requests for the same data are equal and a recomposition does not reload the image.
 */
object FirstFrameDecoderFactory : Decoder.Factory {
    private val bitmapFactoryDecoderFactory = BitmapFactoryDecoder.Factory()

    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
        val source = result.source.source()
        val isAnimated = DecodeUtils.isGif(source) || DecodeUtils.isAnimatedWebP(source) || DecodeUtils.isAnimatedHeif(source)
        return if (isAnimated) bitmapFactoryDecoderFactory.create(result, options, imageLoader) else null
    }
}
