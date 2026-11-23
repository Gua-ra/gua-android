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
 * Counts the events the user sends from this device while the timeline is open, once per transaction id.
 *
 * Only this device's send queue makes local echoes, and only for new events, so history, edits, reactions and other devices' events
 * never count. An echo restored from an earlier session keeps its older timestamp, which [openedAtMillis] filters out.
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
