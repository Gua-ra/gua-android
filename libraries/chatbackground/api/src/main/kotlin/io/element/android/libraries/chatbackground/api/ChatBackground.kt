/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

sealed interface ChatBackground {
    data object None : ChatBackground

    data class Preset(val preset: ChatBackgroundPreset) : ChatBackground

    // Whole numbers because rooms from version 6 refuse an event holding a float.
    data class Photo(
        val url: String,
        val mimetype: String,
        val width: Int,
        val height: Int,
        val size: Long,
        val blurhash: String?,
        /** The opacity in percent of the black layer drawn over the photo in dark mode, 0 to [MAX_DIM]. */
        val dim: Int = DEFAULT_DIM,
        /** Where the photo sits when it overflows the frame horizontally, in thousandths: 0 pins its left edge, [MAX_ALIGN] its right edge. */
        val alignX: Int = DEFAULT_ALIGN,
        /** The same vertically: 0 pins its top edge, [MAX_ALIGN] its bottom edge. */
        val alignY: Int = DEFAULT_ALIGN,
    ) : ChatBackground {
        companion object {
            const val DEFAULT_DIM = 35
            const val MAX_DIM = 80
            const val DEFAULT_ALIGN = 500
            const val MAX_ALIGN = 1000

            /** The largest file a photo background may declare, and the most that is downloaded to show one. */
            const val MAX_SIZE = 20_000_000L
        }
    }
}
