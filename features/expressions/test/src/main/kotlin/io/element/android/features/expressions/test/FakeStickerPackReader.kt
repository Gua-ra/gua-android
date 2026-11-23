/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.test

import io.element.android.features.expressions.api.sticker.StickerPack
import io.element.android.features.expressions.api.sticker.StickerPackReader
import io.element.android.libraries.matrix.api.core.RoomId

class FakeStickerPackReader(
    private val packsResult: (RoomId) -> Result<List<StickerPack>> = { Result.success(emptyList()) },
) : StickerPackReader {
    override suspend fun packs(roomId: RoomId, roomName: String, roomAvatarUrl: String?): Result<List<StickerPack>> = packsResult(roomId)
}
