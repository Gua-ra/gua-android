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
import io.element.android.libraries.matrix.ui.chatbackground.aChatBackgroundPhoto

class ChatBackgroundStatePreviewParam : PreviewParameterProvider<ChatBackgroundState> {
    override val values: Sequence<ChatBackgroundState>
        get() = sequenceOf(
            aChatBackgroundState(),
            aChatBackgroundState(selection = ChatBackground.Preset(ChatBackgroundPreset.Aurora)),
            aChatBackgroundState(current = aChatBackgroundPhoto()),
            aChatBackgroundState(current = ChatBackground.Preset(ChatBackgroundPreset.Sky), canChange = false),
            aChatBackgroundState(
                current = ChatBackground.Preset(ChatBackgroundPreset.Sky),
                selection = ChatBackground.Preset(ChatBackgroundPreset.Sunset),
                action = AsyncAction.Loading,
            ),
            aChatBackgroundState(operation = ChatBackgroundOperation.PreparePhoto, action = AsyncAction.Failure(PhotoTooLargeException())),
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

internal fun aChatBackgroundState(
    current: ChatBackground = ChatBackground.None,
    selection: ChatBackground = current,
    photoDraft: ChatBackgroundPhotoDraft? = null,
    canChange: Boolean = true,
    operation: ChatBackgroundOperation = ChatBackgroundOperation.Set,
    action: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (ChatBackgroundEvent) -> Unit = {},
) = ChatBackgroundState(
    current = current,
    selection = selection,
    photoDraft = photoDraft,
    canChange = canChange,
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
