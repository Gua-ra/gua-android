/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

/** The `global.gua.stickers` account data, read: invalid images are left out. */
data class StickerLibrary(
    val favourites: List<StickerImage>,
    /** My stickers, newest first. */
    val stickers: List<OwnSticker>,
) {
    fun isFavourite(url: String): Boolean = favourites.any { it.url == url }

    companion object {
        val EMPTY = StickerLibrary(favourites = emptyList(), stickers = emptyList())
    }
}

data class OwnSticker(
    val shortcode: String,
    val image: StickerImage,
    /** Milliseconds since the epoch, null when another app added it. */
    val addedAt: Long?,
)

class StickerLimitReachedException : Exception("My stickers already holds ${StickerLibraryCodec.MAX_STICKERS} stickers")
