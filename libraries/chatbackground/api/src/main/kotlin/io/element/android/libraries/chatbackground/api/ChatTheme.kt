/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import androidx.annotation.StringRes

/** A null [bubble] is the stock outgoing bubble. */
data class ChatTheme(
    val background: ChatBackground = ChatBackground.None,
    val bubble: ChatBubbleColor? = null,
) {
    companion object {
        /** No wallpaper and the stock bubble: the same as no theme at all. */
        val STOCK = ChatTheme()
    }
}

@get:StringRes
val ChatTheme.nameRes: Int
    get() = when (background) {
        ChatBackground.None -> bubble?.nameRes ?: background.nameRes
        else -> background.nameRes
    }
