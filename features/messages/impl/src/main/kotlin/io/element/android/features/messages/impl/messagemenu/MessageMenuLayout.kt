/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/** Every rect is in pixels from the container's left edge, whatever the layout direction. */
data class MessageMenuLayout(
    val bubble: Rect,
    val reactionBar: Rect?,
    val details: Rect?,
) {
    companion object {
        fun compute(
            source: Rect,
            alignsRight: Boolean,
            container: Rect,
            reactionBarSize: Size?,
            detailsSize: Size?,
            spacing: Float,
            margin: Float,
            minimumBubbleHeight: Float,
        ): MessageMenuLayout {
            val barSpace = reactionBarSize?.let { it.height + spacing } ?: 0f
            val visibleBubbleHeight = min(source.height, minimumBubbleHeight)
            val detailsHeight = detailsSize?.let { min(it.height, max(0f, container.height - barSpace - visibleBubbleHeight - spacing)) }

            val top = container.top + barSpace
            val bottom = container.bottom - (detailsHeight?.let { it + spacing } ?: 0f)
            val height = max(0f, min(source.height, bottom - top))
            val y = min(max(source.top, top), bottom - height)
            val width = min(source.width, container.width)
            val x = min(max(source.left, container.left), container.right - width)
            val bubble = Rect(left = x, top = y, right = x + width, bottom = y + height)

            fun aligned(size: Size, y: Float): Rect {
                val alignedWidth = min(size.width, container.width - 2 * margin)
                val preferredX = if (alignsRight) bubble.right - alignedWidth else bubble.left
                val alignedX = min(max(preferredX, container.left + margin), container.right - margin - alignedWidth)
                return Rect(left = alignedX, top = y, right = alignedX + alignedWidth, bottom = y + size.height)
            }

            return MessageMenuLayout(
                bubble = bubble,
                reactionBar = reactionBarSize?.let { aligned(it, bubble.top - spacing - it.height) },
                details = if (detailsSize != null && detailsHeight != null) {
                    aligned(Size(detailsSize.width, detailsHeight), bubble.bottom + spacing)
                } else {
                    null
                },
            )
        }
    }
}
