/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import android.net.Uri
import io.element.android.libraries.core.mimetype.MimeTypes

interface ChatBackgroundPhotoProcessor {
    /** The photo upright, in sRGB, at most 2048 px on its longest side, with no metadata from the original file. */
    suspend fun process(uri: Uri): Result<ChatBackgroundPhotoUpload>
}

// Compared by identity: equality over the encoded bytes is never needed.
@Suppress("UseDataClass")
class ChatBackgroundPhotoUpload(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val blurhash: String?,
) {
    val mimetype: String get() = MimeTypes.Jpeg
}
