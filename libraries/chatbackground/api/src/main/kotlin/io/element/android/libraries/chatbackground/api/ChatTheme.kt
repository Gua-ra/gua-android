/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import androidx.annotation.StringRes

/** A null [bubble] is the stock outgoing bubble. A null [bubbleDark] is [bubble] in dark appearance too. */
data class ChatTheme(
    val background: ChatBackground = ChatBackground.None,
    val bubble: ChatBubbleColor? = null,
    val bubbleDark: ChatBubbleColor? = null,
) {
    /** [bubbleDark] counts only next to a [bubble]. */
    fun outgoingBubble(isLight: Boolean): ChatBubbleColor? = if (isLight) bubble else bubble?.let { bubbleDark ?: it }

    /** Drops a [bubbleDark] that would not change the dark appearance, the form the wire holds. */
    fun normalized(): ChatTheme = if (bubbleDark != null && (bubble == null || bubbleDark == bubble)) copy(bubbleDark = null) else this

    companion object {
        /** The same as no theme at all. */
        val STOCK = ChatTheme()
    }
}

@StringRes
fun ChatTheme.nameRes(isLight: Boolean): Int = when (background) {
    ChatBackground.None -> outgoingBubble(isLight)?.nameRes ?: background.nameRes
    else -> background.nameRes
}
