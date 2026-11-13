/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.keyboard

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

class FakeImageFiles(
    private val mimeTypeResult: (Uri) -> String? = { null },
    private val copyResult: (Uri) -> File? = { null },
    private val factsResult: (File, String) -> ImageFacts? = { _, _ -> null },
    private val decodeResult: (File, Int) -> Bitmap? = { _, _ -> null },
    private val writeFirstFrameResult: (File, File) -> Boolean = { _, target ->
        target.writeText("still")
        true
    },
) : ImageFiles {
    override fun mimeType(uri: Uri): String? = mimeTypeResult(uri)

    override fun copy(uri: Uri): File? = copyResult(uri)

    override fun facts(file: File, mimeType: String): ImageFacts? = factsResult(file, mimeType)

    override fun decode(file: File, maxSide: Int): Bitmap? = decodeResult(file, maxSide)

    override fun writeFirstFrame(file: File, target: File): Boolean = writeFirstFrameResult(file, target)
}
