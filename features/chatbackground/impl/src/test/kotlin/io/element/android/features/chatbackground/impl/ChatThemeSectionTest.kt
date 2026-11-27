/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme
import org.junit.Test

class ChatThemeSectionTest {
    @Test
    fun `the gallery sections are the shared ones, in order`() {
        assertThat(ChatThemeSection.entries.map { it.titleRes }).containsExactly(
            R.string.gua_chat_theme_section_featured,
            R.string.gua_chat_theme_section_live,
            R.string.gua_chat_theme_section_nature,
            R.string.gua_chat_theme_section_photos,
            R.string.gua_chat_theme_section_patterns,
            R.string.gua_chat_theme_section_minimal,
            R.string.gua_chat_theme_section_gradients,
        ).inOrder()
    }

    @Test
    fun `Featured offers the picked artworks with their paired bubbles`() {
        assertThat(ChatThemeSection.Featured.themes.map { it.artworkId() }).containsExactly(
            "silk",
            "glacier-lake",
            "glass-drops",
            "currents",
            "palm-coast",
            "ipe",
            "azulejo",
            "paper",
        ).inOrder()
        assertThat(ChatThemeSection.Featured.themes).contains(
            ChatTheme(ChatBackground.Art(ChatArtwork.GlassDrops), ChatBubbleColor.Purple, ChatBubbleColor.Pink)
        )
        assertThat(ChatThemeSection.Featured.themes).contains(ChatTheme(ChatBackground.Art(ChatArtwork.Silk), ChatBubbleColor.Teal))
    }

    @Test
    fun `Live, Nature and Patterns hold their artworks in catalog order`() {
        assertThat(ChatThemeSection.Live.themes.map { it.artworkId() }).containsExactly(
            "glass-drops",
            "glass-drops-blue",
            "molten-pearl",
            "silk",
            "ipe",
            "rain-on-glass",
            "pantanal",
            "currents-live",
        ).inOrder()
        assertThat(ChatThemeSection.Nature.themes.map { it.artworkId() }).containsExactly(
            "dune-lagoons",
            "palm-coast",
            "river-bends",
            "highland-mist",
            "glacier-lake",
            "canola-sky",
            "harbour-fog",
            "canyon-glow",
            "polar-ice",
            "moon-tide",
        ).inOrder()
        assertThat(ChatThemeSection.Patterns.themes.map { it.artworkId() }).containsExactly(
            "currents",
            "azulejo",
            "calcadao",
            "knit",
            "doodles-brasil",
            "doodles-canada",
        ).inOrder()
        assertThat(ChatThemeSection.Live.themes.all { (it.background as ChatBackground.Art).artwork.isLive }).isTrue()
    }

    @Test
    fun `Photos holds the photos in catalog order with their paired bubbles`() {
        assertThat(ChatThemeSection.Photos.themes.map { it.artworkId() }).containsExactly(
            "photo-lencois-lagoon",
            "photo-lencois-dunes",
            "photo-gruta-azul",
            "photo-ipe",
            "photo-araucaria",
            "photo-maned-wolf",
            "photo-maned-wolf-standing",
            "photo-maned-wolf-smiling",
            "photo-yukon-trail",
            "photo-yukon-sun",
            "photo-yukon-forest",
            "photo-yukon-sled",
            "photo-yukon-sled-2",
            "photo-peyto-lake",
            "photo-lone-canoe",
            "photo-jasper-aurora",
            "photo-nunavik-aurora",
            "photo-lac-delage",
            "photo-quebec-ice",
            "photo-quebec-leaves",
        ).inOrder()
        assertThat(ChatThemeSection.Photos.themes).isEqualTo(ChatArtwork.photos.map { it.theme })
        assertThat(ChatThemeSection.Photos.themes).contains(
            ChatTheme(ChatBackground.Art(ChatArtwork.PhotoLencoisLagoon), ChatBubbleColor.Teal, ChatBubbleColor.Purple)
        )
        assertThat(ChatThemeSection.Photos.themes).contains(ChatTheme(ChatBackground.Art(ChatArtwork.PhotoLacDelage), ChatBubbleColor.Blue))
    }

    @Test
    fun `Minimal holds paper, paper-cut, then the six solid presets with their bubbles`() {
        assertThat(ChatThemeSection.Minimal.themes).containsExactly(
            ChatArtwork.Paper.theme,
            ChatTheme(ChatBackground.Art(ChatArtwork.PaperCut), ChatBubbleColor.Teal, ChatBubbleColor.Orange),
            ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Mint), ChatBubbleColor.Green),
            ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Sky), ChatBubbleColor.Blue),
            ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Sand), ChatBubbleColor.Orange),
            ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Blush), ChatBubbleColor.Pink),
            ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Lavender), ChatBubbleColor.Purple),
            ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Graphite), ChatBubbleColor.Graphite),
        ).inOrder()
    }

    @Test
    fun `Gradients offers the eight gradient presets with their bubbles`() {
        assertThat(ChatThemeSection.Gradients.themes.map { (it.background as ChatBackground.Preset).preset to it.bubble }).containsExactly(
            ChatBackgroundPreset.Aurora to ChatBubbleColor.Teal,
            ChatBackgroundPreset.Sunset to ChatBubbleColor.Orange,
            ChatBackgroundPreset.Ocean to ChatBubbleColor.Blue,
            ChatBackgroundPreset.Night to ChatBubbleColor.Purple,
            ChatBackgroundPreset.Dawn to ChatBubbleColor.Pink,
            ChatBackgroundPreset.Forest to ChatBubbleColor.Teal,
            ChatBackgroundPreset.Dusk to ChatBubbleColor.Purple,
            ChatBackgroundPreset.Citrus to ChatBubbleColor.Green,
        ).inOrder()
    }

    @Test
    fun `the wallpaper picker offers every section but Featured`() {
        assertThat(ChatThemeSection.wallpaperSections).containsExactly(
            ChatThemeSection.Live,
            ChatThemeSection.Nature,
            ChatThemeSection.Photos,
            ChatThemeSection.Patterns,
            ChatThemeSection.Minimal,
            ChatThemeSection.Gradients,
        ).inOrder()
    }

    @Test
    fun `no gallery theme holds a dark bubble equal to its bubble`() {
        ChatThemeSection.entries.flatMap { it.themes }.forEach { assertThat(it).isEqualTo(it.normalized()) }
    }
}

private fun ChatTheme.artworkId() = (background as ChatBackground.Art).artwork.id
