/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.mlkit

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dev.zacsweers.metro.Inject
import io.element.android.features.expressions.api.sticker.SubjectCutout
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/** ML Kit subject segmentation. Its model downloads on first use, and until then every cut-out fails. */
@Inject
class MlKitSubjectCutout : SubjectCutout {
    override suspend fun cutOut(image: Bitmap): Bitmap? {
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
        )
        return try {
            withTimeoutOrNull(TIMEOUT) {
                runCatchingExceptions { segmenter.process(InputImage.fromBitmap(image, 0)).await().foregroundBitmap }
                    .onFailure { Timber.w(it, "No subject cut-out") }
                    .getOrNull()
            }
        } finally {
            segmenter.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        val TIMEOUT = 5.seconds
    }
}
