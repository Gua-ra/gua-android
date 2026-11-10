/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoUpload
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme

data class ChatThemeState(
    val mode: ChatThemeMode,
    val current: ChatTheme,
    val defaultTheme: ChatTheme,
    /** Null until the permissions have loaded. */
    val hasPermission: Boolean?,
    val isPhotoHidden: Boolean,
    val page: ChatThemePage,
    val preview: ChatThemePreview?,
    val photoDraft: ChatBackgroundPhotoDraft?,
    val operation: ChatThemeOperation,
    val action: AsyncAction<Unit>,
    val eventSink: (ChatThemeEvent) -> Unit,
) {
    val canChange: Boolean
        get() = hasPermission == true

    val shownCurrent: ChatTheme
        get() = if (isPhotoHidden && current.background is ChatBackground.Photo) current.copy(background = ChatBackground.None) else current

    val defaultTile: ChatTheme
        get() = when (mode) {
            ChatThemeMode.Chat -> defaultTheme
            ChatThemeMode.Default -> ChatTheme.STOCK
        }
}

sealed interface ChatThemePage {
    data object Gallery : ChatThemePage

    data class BubblePicker(val selection: ChatBubbleColor?) : ChatThemePage

    data object WallpaperPicker : ChatThemePage
}

data class ChatThemePreview(
    val shown: ChatTheme,
    /** [ChatTheme.STOCK] for the first tile, which removes the theme rather than copying the default into it. */
    val saved: ChatTheme,
    /** Null follows the app's appearance. */
    val isDarkPreview: Boolean? = null,
)

data class ChatBackgroundPhotoDraft(
    val upload: ChatBackgroundPhotoUpload,
    val dim: Int = ChatBackground.Photo.DEFAULT_DIM,
    val alignX: Int = ChatBackground.Photo.DEFAULT_ALIGN,
    val alignY: Int = ChatBackground.Photo.DEFAULT_ALIGN,
    /** Null follows the app's appearance; dark is the only appearance the dim applies to. */
    val isDarkPreview: Boolean? = null,
)

enum class ChatThemeOperation {
    PreparePhoto,
    Save,
}

class PhotoTooLargeException : Exception("The photo is over the server's upload limit")
