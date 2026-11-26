/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import io.element.android.libraries.matrix.api.core.EventId

data class StickerFile(
    val bytes: ByteArray,
    val mimetype: String,
    val width: Int,
    val height: Int,
    val body: String,
    val isAnimated: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is StickerFile &&
        bytes.contentEquals(other.bytes) &&
        mimetype == other.mimetype &&
        width == other.width &&
        height == other.height &&
        body == other.body &&
        isAnimated == other.isAnimated

    override fun hashCode(): Int = listOf(bytes.contentHashCode(), mimetype, width, height, body, isAnimated).hashCode()
}

sealed class StickerSendException(cause: Throwable) : Exception(cause) {
    class UploadFailed(cause: Throwable) : StickerSendException(cause)

    class SendFailed(cause: Throwable) : StickerSendException(cause)
}

interface StickerSender {
    /** Uploads [file] as plain media, never encrypted, so the image can be reused in any chat. */
    suspend fun upload(file: StickerFile): Result<StickerImage>

    /** Sends an image the server already has; there is no local echo while it sends. */
    suspend fun send(image: StickerImage, relation: StickerRelation): Result<EventId>
}
