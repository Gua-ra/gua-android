/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.api.sticker.StickerFile
import io.element.android.features.expressions.api.sticker.SubjectCutout
import io.element.android.features.expressions.impl.sticker.StickerImageEncoder
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

interface StickerEditorEngine {
    suspend fun load(uri: Uri): Bitmap?

    /** The subjects outlined in white, or null when there is no automatic cut-out. */
    suspend fun cutOut(picture: Bitmap): Bitmap?

    suspend fun render(picture: Bitmap, frame: EditorFrame, text: String): Bitmap

    suspend fun encode(canvas: Bitmap, body: String): StickerFile?
}

@ContributesBinding(AppScope::class)
class AndroidStickerEditorEngine(
    @ApplicationContext private val context: Context,
    private val subjectCutout: SubjectCutout,
    private val encoder: StickerImageEncoder,
    private val dispatchers: CoroutineDispatchers,
) : StickerEditorEngine {
    override suspend fun load(uri: Uri): Bitmap? = withContext(dispatchers.io) {
        tryOrNull {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_PICTURE_SIDE) sampleSize *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            }
        }
    }

    override suspend fun cutOut(picture: Bitmap): Bitmap? {
        val subject = subjectCutout.cutOut(picture) ?: return null
        return withContext(dispatchers.computation) { outlined(subject) }
    }

    override suspend fun render(picture: Bitmap, frame: EditorFrame, text: String): Bitmap = withContext(dispatchers.computation) {
        val side = EditorGeometry.CANVAS_SIZE.toFloat()
        val output = Bitmap.createBitmap(EditorGeometry.CANVAS_SIZE, EditorGeometry.CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val destination = EditorGeometry.destination(picture.width, picture.height, side, frame)
        canvas.drawBitmap(picture, null, RectF(destination.left, destination.top, destination.right, destination.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
        if (text.isNotBlank()) drawText(canvas, text.trim(), side)
        output
    }

    override suspend fun encode(canvas: Bitmap, body: String): StickerFile? = withContext(dispatchers.computation) {
        encoder.encode(canvas, body)
    }

    private fun outlined(subject: Bitmap): Bitmap {
        val width = EditorGeometry.outlineWidth(subject.width, subject.height)
        val padding = width.roundToInt() + 1
        val output = Bitmap.createBitmap(subject.width + 2 * padding, subject.height + 2 * padding, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val silhouette = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) }
        repeat(OUTLINE_STEPS) { step ->
            val angle = 2 * Math.PI * step / OUTLINE_STEPS
            canvas.drawBitmap(subject, padding + width * cos(angle).toFloat(), padding + width * sin(angle).toFloat(), silhouette)
        }
        canvas.drawBitmap(subject, padding.toFloat(), padding.toFloat(), null)
        return output
    }

    private fun drawText(canvas: Canvas, text: String, side: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = EditorGeometry.TEXT_SIZE
        }
        fill.textSize = EditorGeometry.textSize(fill.measureText(text), side)
        val stroke = Paint(fill).apply {
            color = TEXT_OUTLINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = EditorGeometry.TEXT_STROKE_WIDTH
            strokeJoin = Paint.Join.ROUND
        }
        val baseline = side - EditorGeometry.TEXT_MARGIN - fill.descent()
        canvas.drawText(text, side / 2, baseline, stroke)
        canvas.drawText(text, side / 2, baseline, fill)
    }

    private companion object {
        const val MAX_PICTURE_SIDE = 1024
        const val OUTLINE_STEPS = 24
        const val TEXT_OUTLINE_COLOR = 0xE6000000.toInt()
    }
}
