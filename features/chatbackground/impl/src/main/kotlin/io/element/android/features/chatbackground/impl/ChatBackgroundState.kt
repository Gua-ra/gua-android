/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoUpload

data class ChatBackgroundState(
    val current: ChatBackground,
    /** [current] until something else is picked. */
    val selection: ChatBackground,
    /** The picked photo while its full screen preview is open. */
    val photoDraft: ChatBackgroundPhotoDraft?,
    val canChange: Boolean,
    val operation: ChatBackgroundOperation,
    val action: AsyncAction<Unit>,
    val eventSink: (ChatBackgroundEvent) -> Unit,
) {
    val canSet: Boolean get() = canChange && selection != current
    val canRemove: Boolean get() = canChange && current != ChatBackground.None
}

data class ChatBackgroundPhotoDraft(
    val upload: ChatBackgroundPhotoUpload,
    val dim: Double = ChatBackground.Photo.DEFAULT_DIM,
    val alignX: Double = ChatBackground.Photo.DEFAULT_ALIGN,
    val alignY: Double = ChatBackground.Photo.DEFAULT_ALIGN,
    /** Previews the photo as dark mode draws it, the only appearance the dim applies to. */
    val isDarkPreview: Boolean = false,
)

enum class ChatBackgroundOperation {
    PreparePhoto,
    Set,
    Remove,
}

class PhotoTooLargeException : Exception("The photo is over the server's upload limit")
