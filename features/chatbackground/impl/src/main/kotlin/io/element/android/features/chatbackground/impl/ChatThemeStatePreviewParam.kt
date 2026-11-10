/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoUpload
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.matrix.ui.chatbackground.aChatBackgroundPhoto

class ChatThemeStatePreviewParam : PreviewParameterProvider<ChatThemeState> {
    override val values: Sequence<ChatThemeState>
        get() = sequenceOf(
            aChatThemeState(),
            aChatThemeState(
                current = aTheme(ChatBackgroundPreset.Aurora, ChatBubbleColor.Green),
                defaultTheme = aTheme(ChatBackgroundPreset.Dusk, ChatBubbleColor.Orange),
            ),
            aChatThemeState(current = aTheme(ChatBackgroundPreset.Dawn, ChatBubbleColor.Blue), hasPermission = false),
            aChatThemeState(mode = ChatThemeMode.Default),
            aChatThemeState(mode = ChatThemeMode.Default, current = ChatTheme(aChatBackgroundPhoto(), ChatBubbleColor.Teal), action = AsyncAction.Loading),
            aChatThemeState(operation = ChatThemeOperation.PreparePhoto, action = AsyncAction.Failure(PhotoTooLargeException())),
            aChatThemeState(
                current = aTheme(ChatBackgroundPreset.Sunset, ChatBubbleColor.Pink),
                page = ChatThemePage.BubblePicker(selection = ChatBubbleColor.Blue),
            ),
            aChatThemeState(current = aTheme(ChatBackgroundPreset.Night, ChatBubbleColor.Purple), page = ChatThemePage.WallpaperPicker),
            aChatThemeState(
                preview = ChatThemePreview(
                    shown = aTheme(ChatBackgroundPreset.Forest, ChatBubbleColor.Green),
                    saved = aTheme(ChatBackgroundPreset.Forest, ChatBubbleColor.Green),
                ),
            ),
            aChatThemeState(
                mode = ChatThemeMode.Default,
                preview = ChatThemePreview(
                    shown = aTheme(ChatBackgroundPreset.Citrus, ChatBubbleColor.Yellow),
                    saved = aTheme(ChatBackgroundPreset.Citrus, ChatBubbleColor.Yellow),
                    isDarkPreview = true,
                ),
            ),
            aChatThemeState(current = ChatTheme(aChatBackgroundPhoto(), ChatBubbleColor.Blue), isPhotoHidden = true),
            aChatThemeState(current = aTheme(ChatBackgroundPreset.Ocean, ChatBubbleColor.Teal), hasPermission = null),
            aChatThemeState(
                current = aTheme(ChatBackgroundPreset.Night, ChatBubbleColor.Purple),
                page = ChatThemePage.WallpaperPicker,
                preview = ChatThemePreview(
                    shown = aTheme(ChatBackgroundPreset.Mint, ChatBubbleColor.Purple),
                    saved = aTheme(ChatBackgroundPreset.Mint, ChatBubbleColor.Purple),
                ),
            ),
        )
}

class ChatBackgroundPhotoDraftPreviewParam : PreviewParameterProvider<ChatBackgroundPhotoDraft> {
    override val values: Sequence<ChatBackgroundPhotoDraft>
        get() = sequenceOf(
            aChatBackgroundPhotoDraft(),
            aChatBackgroundPhotoDraft(isDarkPreview = true),
            aChatBackgroundPhotoDraft(isDarkPreview = true, dim = ChatBackground.Photo.MAX_DIM, alignX = 0),
        )
}

private fun aTheme(preset: ChatBackgroundPreset, bubble: ChatBubbleColor) = ChatTheme(ChatBackground.Preset(preset), bubble)

internal fun aChatThemeState(
    mode: ChatThemeMode = ChatThemeMode.Chat,
    current: ChatTheme = ChatTheme.STOCK,
    defaultTheme: ChatTheme = ChatTheme.STOCK,
    hasPermission: Boolean? = true,
    isPhotoHidden: Boolean = false,
    page: ChatThemePage = ChatThemePage.Gallery,
    preview: ChatThemePreview? = null,
    photoDraft: ChatBackgroundPhotoDraft? = null,
    operation: ChatThemeOperation = ChatThemeOperation.Save,
    action: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (ChatThemeEvent) -> Unit = {},
) = ChatThemeState(
    mode = mode,
    current = current,
    defaultTheme = defaultTheme,
    hasPermission = hasPermission,
    isPhotoHidden = isPhotoHidden,
    page = page,
    preview = preview,
    photoDraft = photoDraft,
    operation = operation,
    action = action,
    eventSink = eventSink,
)

internal fun aChatBackgroundPhotoDraft(
    isDarkPreview: Boolean? = false,
    dim: Int = ChatBackground.Photo.DEFAULT_DIM,
    alignX: Int = ChatBackground.Photo.DEFAULT_ALIGN,
) = ChatBackgroundPhotoDraft(
    upload = ChatBackgroundPhotoUpload(data = ByteArray(0), width = 2048, height = 1536, blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"),
    dim = dim,
    alignX = alignX,
    isDarkPreview = isDarkPreview,
)
