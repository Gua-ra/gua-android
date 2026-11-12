/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import android.graphics.BitmapFactory
import com.vanniktech.blurhash.BlurHash
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.data.tryOrNull
import java.io.File

fun interface BlurhashEncoder {
    fun encode(image: File): String?
}

@ContributesBinding(AppScope::class)
class AndroidBlurhashEncoder : BlurhashEncoder {
    override fun encode(image: File): String? = tryOrNull {
        val options = BitmapFactory.Options().apply { inSampleSize = SAMPLE_SIZE }
        BitmapFactory.decodeFile(image.path, options)?.let { bitmap ->
            BlurHash.encode(bitmap, COMPONENTS, COMPONENTS).also { bitmap.recycle() }
        }
    }

    private companion object {
        const val SAMPLE_SIZE = 4
        const val COMPONENTS = 3
    }
}
