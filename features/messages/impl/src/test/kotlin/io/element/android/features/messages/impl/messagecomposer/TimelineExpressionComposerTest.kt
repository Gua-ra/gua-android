/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.sticker.StickerRelation
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.libraries.matrix.api.core.asEventId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimelineExpressionComposerTest {
    @Test
    fun `in a thread a sticker replies to the thread's latest event, even when replying to a message`() = runTest {
        val composer = createComposer(
            timeline = FakeTimeline(mode = Timeline.Mode.Thread(A_THREAD_ID), getLatestEventIdResult = { Result.success(AN_EVENT_ID_2) }),
            mode = aReplyMode(),
        )

        assertThat(composer.stickerRelation()).isEqualTo(StickerRelation.Thread(rootId = A_THREAD_ID, latestEventId = AN_EVENT_ID_2))
    }

    @Test
    fun `a thread without a known latest event falls back to its root`() = runTest {
        val composer = createComposer(timeline = FakeTimeline(mode = Timeline.Mode.Thread(A_THREAD_ID), getLatestEventIdResult = { Result.success(null) }))

        assertThat(composer.stickerRelation()).isEqualTo(StickerRelation.Thread(rootId = A_THREAD_ID, latestEventId = A_THREAD_ID.asEventId()))
    }

    @Test
    fun `outside a thread a reply is a reply`() = runTest {
        val reply = aReplyMode()
        val composer = createComposer(mode = reply)

        assertThat(composer.inReplyToEventId).isEqualTo(reply.eventId)
        assertThat(composer.stickerRelation()).isEqualTo(StickerRelation.Reply(reply.eventId))
        assertThat(createComposer().stickerRelation()).isEqualTo(StickerRelation.None)
    }

    @Test
    fun `sending ends a reply and an emoji is inserted as text`() = runTest {
        val events = mutableListOf<MessageComposerEvent>()
        val composer = createComposer(mode = aReplyMode(), events = events)

        composer.onSent()
        composer.insertEmoji("🐈")

        assertThat(events).containsExactly(MessageComposerEvent.CloseSpecialMode, MessageComposerEvent.InsertText("🐈")).inOrder()
    }

    private fun TestScope.createComposer(
        timeline: FakeTimeline = FakeTimeline(),
        mode: MessageComposerMode = MessageComposerMode.Normal,
        events: MutableList<MessageComposerEvent> = mutableListOf(),
    ): TimelineExpressionComposer {
        val room = FakeJoinedRoom(liveTimeline = timeline)
        val controller = TimelineController(
            room = room,
            liveTimeline = timeline,
            roomCoroutineScope = backgroundScope,
            dispatchers = testCoroutineDispatchers(),
        )
        val state = aMessageComposerState(mode = mode, eventSink = { events += it })
        return TimelineExpressionComposer(controller) { state }
    }
}
