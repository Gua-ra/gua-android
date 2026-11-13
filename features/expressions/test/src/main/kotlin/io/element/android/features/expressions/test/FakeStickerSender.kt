/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.test

import io.element.android.features.expressions.api.sticker.StickerFile
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerRelation
import io.element.android.features.expressions.api.sticker.StickerSender
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.tests.testutils.lambda.lambdaError

class FakeStickerSender(
    private val uploadResult: (StickerFile) -> Result<StickerImage> = { lambdaError() },
    private val sendResult: (StickerImage, StickerRelation) -> Result<EventId> = { _, _ -> lambdaError() },
) : StickerSender {
    override suspend fun upload(file: StickerFile): Result<StickerImage> = uploadResult(file)

    override suspend fun send(image: StickerImage, relation: StickerRelation): Result<EventId> = sendResult(image, relation)
}
