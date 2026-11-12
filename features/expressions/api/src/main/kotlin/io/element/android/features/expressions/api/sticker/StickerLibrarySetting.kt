/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import kotlinx.coroutines.flow.StateFlow

/** My stickers and the favourite stickers, synced across the user's devices. */
interface StickerLibrarySetting {
    /** Empty until the cached or stored library has loaded. */
    val library: StateFlow<StickerLibrary>

    fun preload()

    /** Fails with [StickerLimitReachedException] when My stickers is full. */
    suspend fun add(image: StickerImage): Result<Unit>

    suspend fun favourite(image: StickerImage): Result<Unit>

    suspend fun unfavourite(url: String): Result<Unit>

    suspend fun delete(shortcode: String): Result<Unit>
}
