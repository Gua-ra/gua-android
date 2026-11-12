/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrixmedia.api

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest

/** The one check for playing animated images, GIFs and stickers alike: false when Remove animations is on. */
fun Context.shouldAnimateImages(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    ValueAnimator.areAnimatorsEnabled()
} else {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}

/** Decodes only the first frame, cached apart from the animated image. */
fun ImageRequest.Builder.firstFrameOnly(): ImageRequest.Builder = this
    .decoderFactory(BitmapFactoryDecoder.Factory())
    .memoryCacheKeyExtra("frame", "first")
