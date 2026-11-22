/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import androidx.annotation.StringRes

/** In picker order. iOS ships the same IDs and colours. */
enum class ChatBubbleColor(
    val id: String,
    @StringRes val nameRes: Int,
    val light: Long,
    val dark: Long,
) {
    Green(id = "green", nameRes = R.string.gua_chat_theme_bubble_green, light = 0xFFD9F4DD, dark = 0xFF173826),
    Teal(id = "teal", nameRes = R.string.gua_chat_theme_bubble_teal, light = 0xFFD3F1EC, dark = 0xFF123A38),
    Blue(id = "blue", nameRes = R.string.gua_chat_theme_bubble_blue, light = 0xFFDCE8FA, dark = 0xFF18304F),
    Purple(id = "purple", nameRes = R.string.gua_chat_theme_bubble_purple, light = 0xFFE8E0F8, dark = 0xFF30264D),
    Pink(id = "pink", nameRes = R.string.gua_chat_theme_bubble_pink, light = 0xFFF9DDE8, dark = 0xFF4A1F36),
    Orange(id = "orange", nameRes = R.string.gua_chat_theme_bubble_orange, light = 0xFFFBE4D0, dark = 0xFF4A2B14),
    Yellow(id = "yellow", nameRes = R.string.gua_chat_theme_bubble_yellow, light = 0xFFF7F0CC, dark = 0xFF3F3612),
    Graphite(id = "graphite", nameRes = R.string.gua_chat_theme_bubble_graphite, light = 0xFFE1E4E9, dark = 0xFF2F333A);

    fun color(isLight: Boolean): Long = if (isLight) light else dark

    companion object {
        fun fromId(id: String?): ChatBubbleColor? = entries.firstOrNull { it.id == id }
    }
}
