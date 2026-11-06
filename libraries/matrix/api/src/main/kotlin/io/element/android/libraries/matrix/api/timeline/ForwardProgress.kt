/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.timeline

import io.element.android.libraries.matrix.api.core.RoomId
import java.util.concurrent.ConcurrentHashMap

// One per forward, kept while the chat picker is open, so that forwarding to a chat again sends only the events it has not received.
class ForwardProgress(
    private val extraContents: ForwardExtraContents? = null,
) {
    private val sentCounts = ConcurrentHashMap<RoomId, Int>()

    suspend fun forwardTo(roomId: RoomId, eventCount: Int, send: suspend (index: Int, extraContent: String?) -> Unit) {
        val fromIndex = sentCounts[roomId] ?: 0
        if (fromIndex >= eventCount) return
        val chatExtraContents = extraContents?.forChat(roomId, fromIndex)
        var sentCount = fromIndex
        try {
            for (index in fromIndex until eventCount) {
                send(index, chatExtraContents?.getOrNull(index))
                sentCount = index + 1
            }
        } finally {
            // A send that times out throws a CancellationException, which the forwarder rethrows instead of counting the chat as failed.
            sentCounts[roomId] = sentCount
            if (sentCount < eventCount) extraContents?.onUnsent(roomId, sentCount)
        }
    }
}
