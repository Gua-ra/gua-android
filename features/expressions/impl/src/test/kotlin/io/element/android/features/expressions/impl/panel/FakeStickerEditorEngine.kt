/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import android.graphics.Bitmap
import android.net.Uri
import io.element.android.features.expressions.api.sticker.StickerFile
import io.element.android.features.expressions.impl.editor.EditorFrame
import io.element.android.features.expressions.impl.editor.StickerEditorEngine
import io.element.android.tests.testutils.lambda.lambdaError

class FakeStickerEditorEngine(
    private val loadResult: (Uri) -> Bitmap? = { lambdaError() },
    private val cutOutResult: (Bitmap) -> Bitmap? = { null },
    private val renderResult: (Bitmap, EditorFrame, String) -> Bitmap = { picture, _, _ -> picture },
    private val encodeResult: (Bitmap, String) -> StickerFile? = { _, _ -> lambdaError() },
) : StickerEditorEngine {
    override suspend fun load(uri: Uri): Bitmap? = loadResult(uri)

    override suspend fun cutOut(picture: Bitmap): Bitmap? = cutOutResult(picture)

    override suspend fun render(picture: Bitmap, frame: EditorFrame, text: String): Bitmap = renderResult(picture, frame, text)

    override suspend fun encode(canvas: Bitmap, body: String): StickerFile? = encodeResult(canvas, body)
}
