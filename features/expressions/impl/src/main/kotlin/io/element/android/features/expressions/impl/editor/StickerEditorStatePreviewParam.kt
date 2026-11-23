/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.expressions.impl.panel.StickerEditorState

open class StickerEditorStatePreviewParam : PreviewParameterProvider<StickerEditorState> {
    override val values: Sequence<StickerEditorState>
        get() {
            val picture = aPreviewPicture()
            return sequenceOf(
                StickerEditorState(picture = picture, cutOut = picture, isCutOutOn = true, text = "Good morning", isSaving = false, showsNote = true),
                StickerEditorState(picture = picture, cutOut = null, isCutOutOn = false, text = "", isSaving = true, showsNote = false),
                StickerEditorState(picture = null, cutOut = null, isCutOutOn = false, text = "", isSaving = false, showsNote = true),
            )
        }

    private fun aPreviewPicture(): Bitmap = Bitmap.createBitmap(PICTURE_SIZE, PICTURE_SIZE, Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 200, 61) }
        Canvas(bitmap).drawCircle(PICTURE_SIZE / 2f, PICTURE_SIZE / 2f, PICTURE_SIZE * 0.4f, paint)
    }

    private companion object {
        const val PICTURE_SIZE = 256
    }
}
