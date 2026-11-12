/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.compose.runtime.Immutable
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerLibrary
import io.element.android.features.expressions.api.sticker.StickerPack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.text.Normalizer
import java.util.Locale

@Immutable
sealed interface StickerSectionKind {
    data object Recent : StickerSectionKind

    data object Favourites : StickerSectionKind

    data object Mine : StickerSectionKind

    data class Pack(val eventType: String, val stateKey: String, val name: String, val avatarUrl: String?) : StickerSectionKind
}

data class StickerTile(
    val image: StickerImage,
    /** Set for My stickers, which can be deleted by it. */
    val shortcode: String?,
)

data class StickerSection(
    val kind: StickerSectionKind,
    val tiles: ImmutableList<StickerTile>,
)

internal object StickerSections {
    /** Recent always shows, for its Create sticker tile; other sections show only with stickers in them. */
    fun build(
        recents: List<StickerImage>,
        library: StickerLibrary,
        packs: List<StickerPack>,
        query: String,
    ): ImmutableList<StickerSection> {
        val search = normalise(query)
        fun matches(image: StickerImage, shortcode: String?) = search.isEmpty() ||
            normalise(image.body.orEmpty()).contains(search) ||
            normalise(shortcode.orEmpty()).contains(search)

        val recent = StickerSection(StickerSectionKind.Recent, recents.filter { matches(it, null) }.map { StickerTile(it, null) }.toImmutableList())
        val others = buildList {
            add(StickerSection(StickerSectionKind.Favourites, library.favourites.filter { matches(it, null) }.map { StickerTile(it, null) }.toImmutableList()))
            add(
                StickerSection(
                    StickerSectionKind.Mine,
                    library.stickers.filter { matches(it.image, it.shortcode) }.map { StickerTile(it.image, it.shortcode) }.toImmutableList(),
                )
            )
            packs.forEach { pack ->
                val kind = StickerSectionKind.Pack(pack.eventType, pack.stateKey, pack.displayName, pack.avatarUrl)
                add(StickerSection(kind, pack.stickers.filter { matches(it.image, it.shortcode) }.map { StickerTile(it.image, null) }.toImmutableList()))
            }
        }
        return (listOf(recent) + others.filter { it.tiles.isNotEmpty() }).toImmutableList()
    }

    /** Case and accent insensitive. */
    fun normalise(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase(Locale.ROOT)

    private val combiningMarks = Regex("\\p{Mn}+")
}
