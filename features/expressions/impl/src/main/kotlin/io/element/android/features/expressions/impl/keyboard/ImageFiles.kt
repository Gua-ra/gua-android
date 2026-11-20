/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.di.CacheDirectory
import io.element.android.libraries.di.annotations.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max

data class ImageFacts(
    val width: Int,
    val height: Int,
    val isAnimated: Boolean,
    val hasTransparency: Boolean,
)

interface ImageFiles {
    fun mimeType(uri: Uri): String?

    /** Copies the content now: a keyboard's uri may stop being readable once the callback returns. */
    fun copy(uri: Uri): File?

    fun facts(file: File, mimeType: String): ImageFacts?

    fun decode(file: File, maxSide: Int): Bitmap?

    fun writeFirstFrame(file: File, target: File): Boolean
}

@ContributesBinding(AppScope::class)
class AndroidImageFiles(
    @ApplicationContext private val context: Context,
    @CacheDirectory private val cacheDir: File,
) : ImageFiles {
    override fun mimeType(uri: Uri): String? = tryOrNull { context.contentResolver.getType(uri) }

    override fun copy(uri: Uri): File? {
        val target = File(cacheDir, "$DIRECTORY/${UUID.randomUUID()}").apply { parentFile?.mkdirs() }
        val isComplete = tryOrNull {
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyAtMost(it, MAX_SIZE) } }
        } == true
        if (!isComplete) target.delete()
        return target.takeIf { isComplete }
    }

    override fun facts(file: File, mimeType: String): ImageFacts? = tryOrNull {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@tryOrNull null
        val header = ByteArray(ImageHeaders.HEADER_SIZE).also { header -> file.inputStream().use { it.read(header) } }
        ImageFacts(
            width = bounds.outWidth,
            height = bounds.outHeight,
            isAnimated = mimeType == MimeTypes.Gif || ImageHeaders.isAnimatedWebp(header),
            hasTransparency = decode(file, TRANSPARENCY_SAMPLE_SIDE)?.let { bitmap -> bitmap.hasTransparentPixels().also { bitmap.recycle() } } == true,
        )
    }

    override fun decode(file: File, maxSide: Int): Bitmap? = tryOrNull {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSide) sampleSize *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    override fun writeFirstFrame(file: File, target: File): Boolean = tryOrNull {
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return@tryOrNull false
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        true
    } == true

    private fun Bitmap.hasTransparentPixels(): Boolean {
        if (!hasAlpha()) return false
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { it ushr 24 != OPAQUE }
    }

    private fun InputStream.copyAtMost(output: OutputStream, limit: Long): Boolean {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) return true
            total += read
            if (total > limit) return false
            output.write(buffer, 0, read)
        }
    }

    private companion object {
        const val DIRECTORY = "keyboard-content"
        const val MAX_SIZE = 10_000_000L
        const val TRANSPARENCY_SAMPLE_SIDE = 256
        const val JPEG_QUALITY = 80
        const val OPAQUE = 0xFF
    }
}

internal object ImageHeaders {
    const val HEADER_SIZE = 32
    private const val VP8X_FLAGS_OFFSET = 20
    private const val ANIMATION_FLAG = 0x02

    /** Extended WebP files announce animation in the VP8X chunk's flags. */
    fun isAnimatedWebp(header: ByteArray): Boolean {
        if (header.size <= VP8X_FLAGS_OFFSET) return false
        val isWebp = header.ascii(0, 4) == "RIFF" && header.ascii(8, 12) == "WEBP" && header.ascii(12, 16) == "VP8X"
        return isWebp && header[VP8X_FLAGS_OFFSET].toInt() and ANIMATION_FLAG != 0
    }

    private fun ByteArray.ascii(from: Int, to: Int) = String(copyOfRange(from, to), Charsets.US_ASCII)
}
