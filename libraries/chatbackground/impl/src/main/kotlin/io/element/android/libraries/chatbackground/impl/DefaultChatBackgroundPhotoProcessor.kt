/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.vanniktech.blurhash.BlurHash
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.androidutils.bitmap.rotateToExifMetadataOrientation
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoProcessor
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoUpload
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Only decoded pixels are written, so nothing from the original file, EXIF and GPS included, reaches the upload. */
@ContributesBinding(AppScope::class)
class DefaultChatBackgroundPhotoProcessor(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : ChatBackgroundPhotoProcessor {
    override suspend fun process(uri: Uri): Result<ChatBackgroundPhotoUpload> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
                tryOrNull { ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED) }
            } ?: ExifInterface.ORIENTATION_UNDEFINED
            val photo = encode(uri, orientation, MAX_EDGE, QUALITY)
            if (photo.data.size > LARGE_SIZE_BYTES) encode(uri, orientation, REDUCED_MAX_EDGE, REDUCED_QUALITY) else photo
        }
    }

    private fun encode(uri: Uri, orientation: Int, maxEdge: Int, quality: Int): ChatBackgroundPhotoUpload {
        val bitmap = decodeUpright(uri, orientation, maxEdge)
        val data = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "JPEG encoding failed" }
            output.toByteArray()
        }
        val upload = ChatBackgroundPhotoUpload(
            data = data,
            width = bitmap.width,
            height = bitmap.height,
            blurhash = blurhash(bitmap),
        )
        bitmap.recycle()
        return upload
    }

    private fun decodeUpright(uri: Uri, orientation: Int, maxEdge: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        check(options.outWidth > 0 && options.outHeight > 0) { "Not a decodable image" }
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize(max(options.outWidth, options.outHeight), maxEdge)
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Decoding the image failed")
        val upright = decoded.rotateToExifMetadataOrientation(orientation)
        val scale = maxEdge.toFloat() / max(upright.width, upright.height)
        val sized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                upright,
                (upright.width * scale).roundToInt().coerceAtLeast(1),
                (upright.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            upright
        }
        return sized.flattenedOnBlack().also { flattened ->
            listOf(decoded, upright, sized).distinct().filter { it !== flattened }.forEach { it.recycle() }
        }
    }

    private fun Bitmap.flattenedOnBlack(): Bitmap {
        val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.BLACK)
            drawBitmap(this@flattenedOnBlack, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return flattened
    }

    private fun blurhash(bitmap: Bitmap): String? = tryOrNull {
        val height = (BLURHASH_WIDTH * bitmap.height.toFloat() / bitmap.width).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, BLURHASH_WIDTH, height, true)
        BlurHash.encode(small, BLURHASH_COMPONENTS, BLURHASH_COMPONENTS).also { if (small !== bitmap) small.recycle() }
    }

    private fun sampleSize(longestEdge: Int, maxEdge: Int): Int {
        var sampleSize = 1
        while (longestEdge / (sampleSize * 2) >= maxEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        const val MAX_EDGE = 2048
        const val REDUCED_MAX_EDGE = 1600
        private const val QUALITY = 80
        private const val REDUCED_QUALITY = 75
        const val LARGE_SIZE_BYTES = 1_500_000
        private const val BLURHASH_WIDTH = 64
        private const val BLURHASH_COMPONENTS = 3
    }
}
