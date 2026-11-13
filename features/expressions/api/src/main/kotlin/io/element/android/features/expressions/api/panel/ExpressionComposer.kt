/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.panel

import io.element.android.features.expressions.api.sticker.StickerRelation
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.Timeline

interface ExpressionComposer {
    val timelineMode: Timeline.Mode
    val inReplyToEventId: EventId?

    suspend fun stickerRelation(): StickerRelation

    fun insertEmoji(emoji: String)

    fun onSent()
}
