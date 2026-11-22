/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme

sealed interface ChatThemeEvent {
    data class SelectTheme(val theme: ChatTheme) : ChatThemeEvent
    data object SelectDefault : ChatThemeEvent
    data object OpenBubblePicker : ChatThemeEvent
    data class SelectBubble(val bubble: ChatBubbleColor?) : ChatThemeEvent
    data object OpenWallpaperPicker : ChatThemeEvent
    data class SelectWallpaper(val background: ChatBackground) : ChatThemeEvent
    data object ChoosePhoto : ChatThemeEvent
    data class SetPhotoDim(val dim: Int) : ChatThemeEvent
    data class SetPhotoAlign(val x: Int, val y: Int) : ChatThemeEvent
    data class SetDarkPreview(val isDark: Boolean) : ChatThemeEvent
    data object Save : ChatThemeEvent
    data object Cancel : ChatThemeEvent
    data object ClearError : ChatThemeEvent
}
