/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId

sealed interface StickerRelation {
    data object None : StickerRelation

    data class Reply(val eventId: EventId) : StickerRelation

    /**
     * [latestEventId] is the thread's latest event, the reply fallback for clients without threads.
     * A reply to a message inside a thread is sent in this form too.
     */
    data class Thread(val rootId: ThreadId, val latestEventId: EventId) : StickerRelation
}
