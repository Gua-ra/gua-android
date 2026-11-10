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

data class ChatBackgroundState(
    val current: ChatBackground,
    val selection: ChatBackground,
    val photoDraft: ChatBackgroundPhotoDraft?,
    val canChange: Boolean,
    val isPhotoHidden: Boolean,
    val operation: ChatBackgroundOperation,
    val action: AsyncAction<Unit>,
    val eventSink: (ChatBackgroundEvent) -> Unit,
) {
    val canSet: Boolean get() = canChange && selection != current
    val canRemove: Boolean get() = canChange && current != ChatBackground.None
}

data class ChatBackgroundPhotoDraft(
    val upload: ChatBackgroundPhotoUpload,
    val dim: Int = ChatBackground.Photo.DEFAULT_DIM,
    val alignX: Int = ChatBackground.Photo.DEFAULT_ALIGN,
    val alignY: Int = ChatBackground.Photo.DEFAULT_ALIGN,
    /** Null follows the app's appearance; dark is the only appearance the dim applies to. */
    val isDarkPreview: Boolean? = null,
)

enum class ChatBackgroundOperation {
    PreparePhoto,
    Set,
    Remove,
}

class PhotoTooLargeException : Exception("The photo is over the server's upload limit")
