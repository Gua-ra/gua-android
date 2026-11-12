/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.api.sticker.StickerContent
import io.element.android.features.expressions.api.sticker.StickerFile
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerRelation
import io.element.android.features.expressions.api.sticker.StickerSendException
import io.element.android.features.expressions.api.sticker.StickerSender
import io.element.android.libraries.core.extensions.mapFailure
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.room.JoinedRoom

@ContributesBinding(RoomScope::class)
class DefaultStickerSender(
    private val matrixClient: MatrixClient,
    private val room: JoinedRoom,
) : StickerSender {
    override suspend fun upload(file: StickerFile): Result<StickerImage> = matrixClient.uploadMedia(file.mimetype, file.bytes)
        .map { url ->
            StickerContent.image(
                url = url,
                body = file.body,
                pixelWidth = file.width,
                pixelHeight = file.height,
                mimetype = file.mimetype,
                size = file.bytes.size.toLong(),
                isAnimated = file.isAnimated,
            )
        }
        .mapFailure { StickerSendException.UploadFailed(it) }

    override suspend fun send(image: StickerImage, relation: StickerRelation): Result<EventId> =
        room.sendRaw(StickerContent.EVENT_TYPE, StickerContent.encode(image, relation))
            .mapFailure { StickerSendException.SendFailed(it) }
}
