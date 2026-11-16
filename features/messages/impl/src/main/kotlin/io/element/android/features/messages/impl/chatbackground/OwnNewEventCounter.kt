/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatbackground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.matrix.api.core.TransactionId

/**
 * Counts the events the user sends from this device while the timeline is open, for the live wallpaper's send pulse.
 *
 * An event counts once, when its local echo first shows up with a timestamp from [openedAtMillis] on. Only the send queue of this
 * device makes local echoes, and only for new events, whether text, replies, voice, media, stickers, GIFs, polls or locations:
 * history, edits, reactions and redactions never add one, events from the user's other devices arrive without one, and an echo
 * restored from an earlier session's send queue keeps its older timestamp. Retrying a failed send, or its echo becoming the remote
 * event, counts nothing more.
 */
class OwnNewEventCounter(private val openedAtMillis: Long = System.currentTimeMillis()) {
    var count by mutableIntStateOf(0)
        private set

    private val counted = HashSet<TransactionId>()

    fun onTimelineItems(items: List<TimelineItem>) {
        val sent = items.count { item ->
            item is TimelineItem.Event &&
                item.isMine &&
                item.transactionId != null &&
                item.sentTimeMillis >= openedAtMillis &&
                counted.add(item.transactionId)
        }
        if (sent > 0) count += sent
    }
}
