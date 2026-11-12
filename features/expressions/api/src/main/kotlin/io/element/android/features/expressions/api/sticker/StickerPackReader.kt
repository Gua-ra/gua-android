/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import io.element.android.libraries.matrix.api.core.RoomId

interface StickerPackReader {
    suspend fun packs(roomId: RoomId, roomName: String, roomAvatarUrl: String?): Result<List<StickerPack>>
}
