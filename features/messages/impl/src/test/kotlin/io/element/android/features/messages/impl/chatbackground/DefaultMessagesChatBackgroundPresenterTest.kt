/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatbackground

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.test.FakeChatBackgroundService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aMessageContent
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultMessagesChatBackgroundPresenterTest {
    @Test
    fun `the main timeline shows the chat background`() = runTest {
        val service = FakeChatBackgroundService(ChatBackground.Preset(ChatBackgroundPreset.Ocean))
        val presenter = createDefaultMessagesChatBackgroundPresenter(service = service)

        presenter.test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            assertThat(awaitItem()).isEqualTo(ChatBackground.Preset(ChatBackgroundPreset.Ocean))
        }
    }

    @Test
    fun `threads keep the default look`() = runTest {
        val service = FakeChatBackgroundService(ChatBackground.Preset(ChatBackgroundPreset.Ocean))
        val presenter = createDefaultMessagesChatBackgroundPresenter(service = service, timeline = FakeTimeline(mode = Timeline.Mode.Thread(A_THREAD_ID)))

        presenter.test {
            assertThat(awaitItem()).isEqualTo(ChatBackground.None)
            expectNoEvents()
        }
        assertThat(service.timelineEvents).isEmpty()
    }

    @Test
    fun `the newest background event in the timeline is reported once`() = runTest {
        val items = MutableStateFlow<List<MatrixTimelineItem>>(
            listOf(
                aBackgroundItem("\$old", timestamp = 10, contentJson = "{}"),
                aBackgroundItem("\$new", timestamp = 20, contentJson = """{"v":1,"kind":"preset","preset":"sky"}"""),
                MatrixTimelineItem.Event(UniqueId("message"), anEventTimelineItem(eventId = EventId("\$message"), timestamp = 30, content = aMessageContent())),
            )
        )
        val service = FakeChatBackgroundService()
        val presenter = createDefaultMessagesChatBackgroundPresenter(service = service, timeline = FakeTimeline(timelineItems = items))

        presenter.test {
            awaitItem()
            items.value =
                items.value + MatrixTimelineItem.Event(UniqueId("other"), anEventTimelineItem(eventId = EventId("\$other"), content = aMessageContent()))
            items.value = items.value + aBackgroundItem("\$newest", timestamp = 40, contentJson = null)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(service.timelineEvents).containsExactly(
            FakeChatBackgroundService.TimelineEvent(A_ROOM_ID, EventId("\$new"), 20, """{"v":1,"kind":"preset","preset":"sky"}"""),
            FakeChatBackgroundService.TimelineEvent(A_ROOM_ID, EventId("\$newest"), 40, null),
        ).inOrder()
    }

    private fun TestScope.createDefaultMessagesChatBackgroundPresenter(
        service: FakeChatBackgroundService,
        timeline: FakeTimeline = FakeTimeline(),
    ): DefaultMessagesChatBackgroundPresenter {
        val room = FakeJoinedRoom(liveTimeline = timeline)
        return DefaultMessagesChatBackgroundPresenter(
            timelineController = TimelineController(
                room = room,
                liveTimeline = timeline,
                roomCoroutineScope = backgroundScope,
                dispatchers = testCoroutineDispatchers(),
            ),
            room = room,
            chatBackgroundService = service,
        )
    }
}

private fun aBackgroundItem(eventId: String, timestamp: Long, contentJson: String?) = MatrixTimelineItem.Event(
    uniqueId = UniqueId(eventId),
    event = anEventTimelineItem(
        eventId = EventId(eventId),
        timestamp = timestamp,
        content = StateContent("", OtherState.ChatBackground(contentJson)),
    ),
)
