/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.gif

enum class GifSize(val key: String) {
    Hd("hd"),
    Md("md"),
    Sm("sm"),
    Xs("xs"),
}

data class GifFile(
    val url: String,
    val width: Int,
    val height: Int,
    val size: Long,
)

data class GifRendition(
    val animated: GifFile?,
    val still: GifFile?,
)

data class GifItem(
    /** What favourites and recents store; the provider looks items up by it. */
    val id: String,
    val title: String,
    val renditions: Map<GifSize, GifRendition>,
) {
    val preview: GifFile? get() = listOf(GifSize.Sm, GifSize.Xs, GifSize.Md, GifSize.Hd).firstNotNullOfOrNull { renditions[it]?.animated }
}

data class GifPage(
    val items: List<GifItem>,
    val page: Int,
    val hasNext: Boolean,
)
