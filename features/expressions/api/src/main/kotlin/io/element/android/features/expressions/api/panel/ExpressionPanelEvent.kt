/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.panel

import android.net.Uri
import io.element.android.features.expressions.api.sticker.StickerImage

sealed interface ExpressionPanelEvent {
    data object Open : ExpressionPanelEvent

    data object Close : ExpressionPanelEvent

    data class ShowTimelineSticker(val image: StickerImage) : ExpressionPanelEvent

    /** [fallback] sends the content as it was sent before stickers and GIFs existed. */
    data class ReceiveKeyboardContent(val content: KeyboardContent, val fallback: () -> Unit) : ExpressionPanelEvent
}

/** Content a keyboard inserted or the user pasted. Its uri is readable only until the app releases it. */
data class KeyboardContent(
    val uri: Uri,
    val mimeType: String?,
    val isFromInputMethod: Boolean,
)
