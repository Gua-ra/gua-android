/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.media.isPreviewEnabled
import io.element.android.libraries.matrix.api.room.join.JoinRule

/** A chat photo that the media setting hides falls back to the default as a whole, while the default's own photo always shows. */
fun effectiveChatTheme(
    chatTheme: ChatTheme,
    defaultTheme: ChatTheme,
    roomIsPrivate: Boolean,
    mediaShown: Boolean,
): ChatTheme {
    if (chatTheme == ChatTheme.STOCK) return defaultTheme
    val isPhotoHidden = chatTheme.background is ChatBackground.Photo && isChatPhotoHidden(roomIsPrivate, mediaShown)
    return if (isPhotoHidden) defaultTheme else chatTheme
}

fun isChatPhotoHidden(roomIsPrivate: Boolean, mediaShown: Boolean) = !roomIsPrivate && !mediaShown

/** The rooms that the "in private chats" media setting treats as private. */
fun JoinRule?.isPrivateChat(): Boolean = MediaPreviewValue.Private.isPreviewEnabled(this)
