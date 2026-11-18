/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.annotation.StringRes
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.chatbackground.api.ChatArtworkSection
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme

/** iOS shows the same sections and tiles, in the same order. */
enum class ChatThemeSection(@StringRes val titleRes: Int, val themes: List<ChatTheme>) {
    Featured(
        titleRes = R.string.gua_chat_theme_section_featured,
        themes = listOf(
            ChatArtwork.Silk,
            ChatArtwork.GlacierLake,
            ChatArtwork.GlassDrops,
            ChatArtwork.Currents,
            ChatArtwork.PalmCoast,
            ChatArtwork.Ipe,
            ChatArtwork.Azulejo,
            ChatArtwork.Paper,
        ).map { it.theme },
    ),
    Live(
        titleRes = R.string.gua_chat_theme_section_live,
        themes = artworkThemes(ChatArtworkSection.Live),
    ),
    Nature(
        titleRes = R.string.gua_chat_theme_section_nature,
        themes = artworkThemes(ChatArtworkSection.Nature),
    ),
    Photos(
        titleRes = R.string.gua_chat_theme_section_photos,
        themes = artworkThemes(ChatArtworkSection.Photos),
    ),
    Patterns(
        titleRes = R.string.gua_chat_theme_section_patterns,
        themes = artworkThemes(ChatArtworkSection.Patterns),
    ),
    Minimal(
        titleRes = R.string.gua_chat_theme_section_minimal,
        themes = artworkThemes(ChatArtworkSection.Minimal) + listOf(
            theme(ChatBackgroundPreset.Mint, ChatBubbleColor.Green),
            theme(ChatBackgroundPreset.Sky, ChatBubbleColor.Blue),
            theme(ChatBackgroundPreset.Sand, ChatBubbleColor.Orange),
            theme(ChatBackgroundPreset.Blush, ChatBubbleColor.Pink),
            theme(ChatBackgroundPreset.Lavender, ChatBubbleColor.Purple),
            theme(ChatBackgroundPreset.Graphite, ChatBubbleColor.Graphite),
        ),
    ),
    Gradients(
        titleRes = R.string.gua_chat_theme_section_gradients,
        themes = listOf(
            theme(ChatBackgroundPreset.Aurora, ChatBubbleColor.Teal),
            theme(ChatBackgroundPreset.Sunset, ChatBubbleColor.Orange),
            theme(ChatBackgroundPreset.Ocean, ChatBubbleColor.Blue),
            theme(ChatBackgroundPreset.Night, ChatBubbleColor.Purple),
            theme(ChatBackgroundPreset.Dawn, ChatBubbleColor.Pink),
            theme(ChatBackgroundPreset.Forest, ChatBubbleColor.Teal),
            theme(ChatBackgroundPreset.Dusk, ChatBubbleColor.Purple),
            theme(ChatBackgroundPreset.Citrus, ChatBubbleColor.Green),
        ),
    );

    companion object {
        /** The wallpaper picker's rows, which offer each section's wallpapers without their bubbles. */
        val wallpaperSections = listOf(Live, Nature, Photos, Patterns, Minimal, Gradients)
    }
}

private fun artworkThemes(section: ChatArtworkSection) = ChatArtwork.entries.filter { it.section == section }.map { it.theme }

private fun theme(preset: ChatBackgroundPreset, bubble: ChatBubbleColor) = ChatTheme(ChatBackground.Preset(preset), bubble)
