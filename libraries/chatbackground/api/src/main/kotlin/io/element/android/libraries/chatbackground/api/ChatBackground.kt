/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import androidx.annotation.StringRes

sealed interface ChatBackground {
    data object None : ChatBackground

    data class Preset(val preset: ChatBackgroundPreset) : ChatBackground

    // Integers only: room versions 6 and later reject events that contain floats.
    data class Photo(
        val url: String,
        val mimetype: String,
        val width: Int,
        val height: Int,
        val size: Long,
        val blurhash: String?,
        /** Opacity in percent of the black layer over the photo in dark mode. */
        val dim: Int = DEFAULT_DIM,
        /** Horizontal position of an overflowing photo, in thousandths: 0 pins its left edge, [MAX_ALIGN] its right edge. */
        val alignX: Int = DEFAULT_ALIGN,
        /** The same vertically: 0 pins its top edge, [MAX_ALIGN] its bottom edge. */
        val alignY: Int = DEFAULT_ALIGN,
    ) : ChatBackground {
        companion object {
            const val DEFAULT_DIM = 35
            const val MAX_DIM = 80
            const val DEFAULT_ALIGN = 500
            const val MAX_ALIGN = 1000

            /** The largest file a photo background may declare, and the largest downloaded file that is decoded. */
            const val MAX_SIZE = 20_000_000L
        }
    }
}

@get:StringRes
val ChatBackground.nameRes: Int
    get() = when (this) {
        ChatBackground.None -> R.string.gua_chat_background_value_default
        is ChatBackground.Preset -> preset.nameRes
        is ChatBackground.Photo -> R.string.gua_chat_background_value_photo
    }
