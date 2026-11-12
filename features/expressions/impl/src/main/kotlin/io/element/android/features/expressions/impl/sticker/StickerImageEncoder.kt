/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import dev.zacsweers.metro.Inject
import io.element.android.features.expressions.api.sticker.StickerContent
import io.element.android.features.expressions.api.sticker.StickerFile
import io.element.android.libraries.core.mimetype.MimeTypes
import java.io.ByteArrayOutputStream

/** Turns an edited picture into a sticker file: trimmed, fitted, and at most [MAX_FILE_SIZE] when it can be. */
@Inject
class StickerImageEncoder {
    fun encode(source: Bitmap, body: String): StickerFile? {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val bounds = StickerGeometry.opaqueBounds(source.width, source.height, pixels) ?: return null
        val content = StickerGeometry.fittedContentSize(bounds.width, bounds.height)
        var canvasSize = StickerGeometry.canvasSize(content)
        val full = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        Canvas(full).drawBitmap(
            source,
            Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            Rect(StickerGeometry.PADDING, StickerGeometry.PADDING, StickerGeometry.PADDING + content.width, StickerGeometry.PADDING + content.height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        var bytes = compress(full)
        while (bytes.size > MAX_FILE_SIZE) {
            canvasSize = StickerGeometry.shrunk(canvasSize) ?: break
            bytes = Bitmap.createScaledBitmap(full, canvasSize.width, canvasSize.height, true).let { smaller ->
                compress(smaller).also { smaller.recycle() }
            }
        }
        full.recycle()
        return StickerFile(
            bytes = bytes,
            mimetype = if (usesWebp) MimeTypes.WebP else MimeTypes.Png,
            width = canvasSize.width,
            height = canvasSize.height,
            body = body.trim().ifEmpty { StickerContent.DEFAULT_BODY },
            isAnimated = false,
        )
    }

    private val usesWebp: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun compress(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { output ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, output)
        } else {
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
        }
        output.toByteArray()
    }

    companion object {
        const val MAX_FILE_SIZE = 300_000
        private const val WEBP_QUALITY = 90

        // PNG is lossless and ignores the quality.
        private const val PNG_QUALITY = 100
    }
}
