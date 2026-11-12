/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.gif

data class GifSelection(
    val size: GifSize,
    val file: GifFile,
    val still: GifFile?,
    val body: String,
)

object GifRenditions {
    const val MAX_HD_SIZE = 1_500_000L
    const val DEFAULT_BODY = "GIF"

    private val sendOrder = listOf(GifSize.Hd, GifSize.Md, GifSize.Sm, GifSize.Xs)

    /** `hd` when it is at most [MAX_HD_SIZE], else the next size down. */
    fun forSending(item: GifItem): GifSelection? = sendOrder.firstNotNullOfOrNull { size ->
        val rendition = item.renditions[size]
        rendition?.animated
            ?.takeIf { size != GifSize.Hd || it.size <= MAX_HD_SIZE }
            ?.let { file ->
                GifSelection(
                    size = size,
                    file = file,
                    still = rendition.still,
                    body = item.title.trim().ifEmpty { DEFAULT_BODY },
                )
            }
    }
}
