/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.annotation.StringRes
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme

/** iOS shows the same sections and tiles, in the same order. */
enum class ChatThemeSection(@StringRes val titleRes: Int, val themes: List<ChatTheme>) {
    Featured(
        titleRes = R.string.gua_chat_theme_section_featured,
        themes = listOf(
            theme(ChatBackgroundPreset.Aurora, ChatBubbleColor.Green),
            theme(ChatBackgroundPreset.Ocean, ChatBubbleColor.Teal),
            theme(ChatBackgroundPreset.Sunset, ChatBubbleColor.Pink),
            theme(ChatBackgroundPreset.Night, ChatBubbleColor.Purple),
            theme(ChatBackgroundPreset.Dawn, ChatBubbleColor.Blue),
            theme(ChatBackgroundPreset.Forest, ChatBubbleColor.Green),
            theme(ChatBackgroundPreset.Dusk, ChatBubbleColor.Orange),
            theme(ChatBackgroundPreset.Citrus, ChatBubbleColor.Yellow),
        ),
    ),
    Colours(
        titleRes = R.string.gua_chat_theme_section_colours,
        themes = listOf(
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
        val wallpaperSections = listOf(Colours, Gradients)
    }
}

private fun theme(preset: ChatBackgroundPreset, bubble: ChatBubbleColor) = ChatTheme(ChatBackground.Preset(preset), bubble)
