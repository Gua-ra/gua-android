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
    /** The outgoing bubble in the given appearance, null for the stock one. [bubbleDark] counts only next to a palette [bubble]. */
    fun outgoingBubble(isLight: Boolean): ChatBubbleColor? = if (isLight) bubble else bubble?.let { bubbleDark ?: it }

    /** The same theme with [bubbleDark] kept only where it changes the dark appearance, as the wire holds it. */
    fun normalized(): ChatTheme = if (bubbleDark != null && (bubble == null || bubbleDark == bubble)) copy(bubbleDark = null) else this

    companion object {
        /** No wallpaper and the stock bubble: the same as no theme at all. */
        val STOCK = ChatTheme()
    }
}

/** A theme with no wallpaper is named after the bubble shown in the given appearance. */
@StringRes
fun ChatTheme.nameRes(isLight: Boolean): Int = when (background) {
    ChatBackground.None -> outgoingBubble(isLight)?.nameRes ?: background.nameRes
    else -> background.nameRes
}
