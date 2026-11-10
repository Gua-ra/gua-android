/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.timeline

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import java.util.concurrent.ConcurrentHashMap

// One per forward, kept while the chat picker is open, so that forwarding to a chat again sends only the events it has not received.
class ForwardProgress(
    private val eventIds: List<EventId>,
    private val extraContents: ForwardExtraContents? = null,
) {
    private val sentCounts = ConcurrentHashMap<RoomId, Int>()

    fun hasSentAll(roomId: RoomId): Boolean = sentCounts[roomId] == eventIds.size

    suspend fun forwardTo(roomId: RoomId, eventIds: List<EventId>, send: suspend (index: Int, extraContent: String?) -> Unit) {
        // The counts cover the whole forward, so a call carrying only some of its events would skip the rest and still succeed.
        require(eventIds == this.eventIds) { "A resumable forward must send all ${this.eventIds.size} events in one call, not ${eventIds.size}" }
        val fromIndex = sentCounts[roomId] ?: 0
        if (fromIndex >= eventIds.size) return
        val chatExtraContents = extraContents?.forChat(roomId, fromIndex)
        var sentCount = fromIndex
        try {
            for (index in fromIndex until eventIds.size) {
                send(index, chatExtraContents?.getOrNull(index))
                sentCount = index + 1
            }
        } finally {
            // A send that times out throws a CancellationException, which the forwarder rethrows instead of counting the chat as failed.
            sentCounts[roomId] = sentCount
            if (sentCount < eventIds.size) extraContents?.onUnsent(roomId, sentCount)
        }
    }
}
