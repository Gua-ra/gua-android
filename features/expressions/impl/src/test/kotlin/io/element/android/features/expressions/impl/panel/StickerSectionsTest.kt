/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.sticker.OwnSticker
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerLibrary
import org.junit.Test

class StickerSectionsTest {
    private val cat = StickerImage(url = "mxc://gua.global/cat", body = "Gato Feliz", info = null)

    @Test
    fun `Recent always shows, for its Create sticker tile, while empty sections are left out`() {
        val sections = StickerSections.build(recents = emptyList(), library = StickerLibrary.EMPTY, packs = emptyList(), query = "")

        assertThat(sections.map { it.kind }).containsExactly(StickerSectionKind.Recent)
    }

    @Test
    fun `favourites come before my stickers`() {
        val library = StickerLibrary(favourites = listOf(cat), stickers = listOf(OwnSticker("s1", cat, 1)))

        val sections = StickerSections.build(recents = emptyList(), library = library, packs = emptyList(), query = "")

        assertThat(sections.map { it.kind }).containsExactly(StickerSectionKind.Recent, StickerSectionKind.Favourites, StickerSectionKind.Mine).inOrder()
        assertThat(sections.last().tiles.single().shortcode).isEqualTo("s1")
    }

    @Test
    fun `search ignores case and accents`() {
        assertThat(StickerSections.normalise("  ÉLAN Café ")).isEqualTo("elan cafe")

        val sections = StickerSections.build(recents = listOf(cat), library = StickerLibrary.EMPTY, packs = emptyList(), query = "FELÍZ")

        assertThat(sections.single().tiles).hasSize(1)
    }
}
