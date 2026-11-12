/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import kotlinx.coroutines.flow.Flow

/** The stickers sent from this device, newest first. */
interface RecentStickersStore {
    val recents: Flow<List<StickerImage>>

    suspend fun add(image: StickerImage)

    suspend fun remove(url: String)

    companion object {
        const val MAX_RECENTS = 30
    }
}
