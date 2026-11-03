/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.test

import android.net.Uri
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoProcessor
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoUpload

class FakeChatBackgroundPhotoProcessor(
    private val result: (Uri) -> Result<ChatBackgroundPhotoUpload> = { Result.success(aChatBackgroundPhotoUpload()) },
) : ChatBackgroundPhotoProcessor {
    override suspend fun process(uri: Uri): Result<ChatBackgroundPhotoUpload> = result(uri)
}

fun aChatBackgroundPhotoUpload(
    size: Int = 1_000,
    width: Int = 1536,
    height: Int = 2048,
    blurhash: String? = "LKO2?U%2Tw=w]~RBVZRi};RPxuwH",
) = ChatBackgroundPhotoUpload(data = ByteArray(size), width = width, height = height, blurhash = blurhash)
