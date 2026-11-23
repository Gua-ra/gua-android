/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatbackground

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineItemReactions
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.aStaticLocationMode
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemLocationContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemPollContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemStickerContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemVoiceContent
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.TransactionId
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetails
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

private const val OPENED_AT = 1_000_000L

class OwnNewEventCounterTest {
    private val history = listOf(
        aTimelineItemEvent(isMine = true).copy(sentTimeMillis = OPENED_AT - 60_000),
        aTimelineItemEvent(isMine = false).copy(sentTimeMillis = OPENED_AT - 30_000),
    )

    @Test
    fun `history counts nothing`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        counter.onTimelineItems(history)
        assertThat(counter.count).isEqualTo(0)
    }

    @Test
    fun `each send from this device counts once`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        counter.onTimelineItems(history)
        val first = aLocalEcho("first", sentAt = OPENED_AT + 1)
        counter.onTimelineItems(listOf(first) + history)
        assertThat(counter.count).isEqualTo(1)
        val second = aLocalEcho("second", sentAt = OPENED_AT + 2)
        counter.onTimelineItems(listOf(second, first) + history)
        assertThat(counter.count).isEqualTo(2)
    }

    @Test
    fun `several sends at once count each`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        counter.onTimelineItems(listOf(aLocalEcho("a", OPENED_AT + 1), aLocalEcho("b", OPENED_AT + 1), aLocalEcho("c", OPENED_AT + 1)))
        assertThat(counter.count).isEqualTo(3)
    }

    @Test
    fun `every kind of message sent from this device counts`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        val sends = listOf(
            aLocalEcho("text", OPENED_AT + 1),
            aLocalEcho("reply", OPENED_AT + 1).copy(inReplyTo = InReplyToDetails.Loading(EventId("\$original"))),
            aLocalEcho("voice", OPENED_AT + 1).copy(content = aTimelineItemVoiceContent(eventId = null)),
            aLocalEcho("image or GIF", OPENED_AT + 1).copy(content = aTimelineItemImageContent()),
            aLocalEcho("poll", OPENED_AT + 1).copy(content = aTimelineItemPollContent(isMine = true)),
            aLocalEcho("location", OPENED_AT + 1).copy(content = aTimelineItemLocationContent(mode = aStaticLocationMode())),
        )
        sends.forEachIndexed { index, send ->
            counter.onTimelineItems(sends.take(index + 1).reversed())
            assertThat(counter.count).isEqualTo(index + 1)
        }
    }

    @Test
    fun `a sticker counts once as it is sent, and its remote event counts nothing more`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        counter.onTimelineItems(history)
        counter.onStickerSent()
        assertThat(counter.count).isEqualTo(1)
        val remoteSticker = aTimelineItemEvent(isMine = true, content = aTimelineItemStickerContent()).copy(sentTimeMillis = OPENED_AT + 1)
        counter.onTimelineItems(listOf(remoteSticker) + history)
        assertThat(counter.count).isEqualTo(1)
    }

    @Test
    fun `a GIF counts once through its local echo, next to a sticker sent through the direct path`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        counter.onStickerSent()
        val gif = aLocalEcho("gif", OPENED_AT + 1).copy(content = aTimelineItemImageContent())
        counter.onTimelineItems(listOf(gif))
        assertThat(counter.count).isEqualTo(2)
        counter.onTimelineItems(listOf(gif.copy(transactionId = null, localSendState = null)))
        assertThat(counter.count).isEqualTo(2)
    }

    @Test
    fun `an edit, a reaction, a retry or the remote echo of a send counts nothing more`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        val echo = aLocalEcho("txn", sentAt = OPENED_AT + 1)
        counter.onTimelineItems(listOf(echo))
        counter.onTimelineItems(listOf(echo.copy(content = aTimelineItemTextContent(body = "Edited"))))
        counter.onTimelineItems(listOf(echo.copy(reactionsState = aTimelineItemReactions(count = 1))))
        counter.onTimelineItems(listOf(echo.copy(localSendState = LocalEventSendState.Failed.Unknown("error"))))
        counter.onTimelineItems(listOf(echo.copy(localSendState = LocalEventSendState.Sending.Event)))
        counter.onTimelineItems(listOf(echo.copy(transactionId = null, localSendState = null)))
        assertThat(counter.count).isEqualTo(1)
    }

    @Test
    fun `own events from another device count nothing`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        counter.onTimelineItems(listOf(aTimelineItemEvent(isMine = true).copy(sentTimeMillis = OPENED_AT + 5)))
        assertThat(counter.count).isEqualTo(0)
    }

    @Test
    fun `an unsent echo from before the timeline opened counts nothing`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        counter.onTimelineItems(listOf(aLocalEcho("restored", sentAt = OPENED_AT - 1)))
        assertThat(counter.count).isEqualTo(0)
    }

    @Test
    fun `items that are not events count nothing`() {
        val counter = OwnNewEventCounter(openedAtMillis = OPENED_AT)
        val grouped = TimelineItem.GroupedEvents(
            id = aLocalEcho("grouped", OPENED_AT + 1).id,
            events = persistentListOf(aLocalEcho("inner", OPENED_AT + 1)),
            aggregatedReadReceipts = persistentListOf(),
        )
        counter.onTimelineItems(listOf(grouped))
        assertThat(counter.count).isEqualTo(0)
    }

    private fun aLocalEcho(transactionId: String, sentAt: Long) = aTimelineItemEvent(
        transactionId = TransactionId(transactionId),
        isMine = true,
        sendState = LocalEventSendState.Sending.Event,
    ).copy(eventId = null, sentTimeMillis = sentAt)
}
