/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

sealed interface ChatBackground {
    data object None : ChatBackground

    data class Preset(val preset: ChatBackgroundPreset) : ChatBackground

    data class Photo(
        val url: String,
        val mimetype: String,
        val width: Int,
        val height: Int,
        val size: Long,
        val blurhash: String?,
        /** The opacity of the black layer drawn over the photo in dark mode, 0 to [MAX_DIM]. */
        val dim: Double = DEFAULT_DIM,
        /** Where the photo sits when it overflows the frame horizontally: 0 pins its left edge, 1 its right edge. */
        val alignX: Double = DEFAULT_ALIGN,
        /** The same vertically: 0 pins its top edge, 1 its bottom edge. */
        val alignY: Double = DEFAULT_ALIGN,
    ) : ChatBackground {
        companion object {
            const val DEFAULT_DIM = 0.35
            const val MAX_DIM = 0.8
            const val DEFAULT_ALIGN = 0.5

            /** The largest file a photo background may declare, and the most that is downloaded to show one. */
            const val MAX_SIZE = 20_000_000L
        }
    }
}
