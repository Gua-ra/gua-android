/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrixmedia.test

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import io.element.android.libraries.matrixmedia.api.FirstFrameDecoderFactory
import java.util.concurrent.CopyOnWriteArrayList

/** Loads nothing and keeps every request it is given, to check how an image was asked for. */
class RecordingImageLoader(context: Context) {
    val requests: List<ImageRequest> get() = recorded

    private val recorded = CopyOnWriteArrayList<ImageRequest>()

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    recorded += chain.request
                    return ErrorResult(image = null, request = chain.request, throwable = IllegalStateException("Images are not loaded in tests"))
                }
            })
        }
        .build()
}

/** Whether the request decodes the first frame only, so an animated image stays still. */
val ImageRequest.isFirstFrameOnly: Boolean
    get() = decoderFactory === FirstFrameDecoderFactory

/** Runs [block] with Remove animations turned on in the system settings. */
fun withSystemAnimationsOff(context: Context, block: () -> Unit) {
    setSystemAnimationScale(context, 0f)
    try {
        block()
    } finally {
        setSystemAnimationScale(context, 1f)
    }
}

private fun setSystemAnimationScale(context: Context, scale: Float) {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    // Hidden API: the system calls it when the setting changes, which does not happen under Robolectric.
    ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
}
