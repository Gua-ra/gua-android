/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatbackground

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.chatbackground.test.FakeChatBackgroundService
import io.element.android.libraries.chatbackground.test.FakeChatThemeSetting
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.media.MediaPreviewConfig
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.room.join.JoinRule
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.libraries.matrix.test.media.FakeMediaPreviewService
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aMessageContent
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import io.element.android.libraries.matrix.ui.chatbackground.aChatBackgroundPhoto
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultMessagesChatBackgroundPresenterTest {
    @Test
    fun `the main timeline shows the chat theme from its first frame`() = runTest {
        val service = FakeChatBackgroundService(ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Ocean)))
        val presenter = createDefaultMessagesChatBackgroundPresenter(service = service)

        presenter.test {
            assertThat(awaitItem()).isEqualTo(ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Ocean)))
            service.theme.value = ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Sand), ChatBubbleColor.Orange)
            assertThat(awaitItem()).isEqualTo(ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Sand), ChatBubbleColor.Orange))
        }
    }

    @Test
    fun `threads keep the stock look`() = runTest {
        val service = FakeChatBackgroundService(ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Ocean)))
        val presenter = createDefaultMessagesChatBackgroundPresenter(
            service = service,
            setting = FakeChatThemeSetting(ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Dawn))),
            timeline = FakeTimeline(mode = Timeline.Mode.Thread(A_THREAD_ID)),
        )

        presenter.test {
            assertThat(awaitItem()).isEqualTo(ChatTheme.STOCK)
            expectNoEvents()
        }
        assertThat(service.timelineEvents).isEmpty()
    }

    @Test
    fun `a chat without a theme shows the personal default, and follows it`() = runTest {
        val setting = FakeChatThemeSetting(defaultTheme)
        val presenter = createDefaultMessagesChatBackgroundPresenter(service = FakeChatBackgroundService(), setting = setting)

        presenter.test {
            assertThat(awaitItem()).isEqualTo(defaultTheme)
            setting.theme.value = ChatTheme.STOCK
            assertThat(awaitItem()).isEqualTo(ChatTheme.STOCK)
        }
    }

    @Test
    fun `a chat photo hidden by the media setting in a public room falls back to the default, and shows again with media`() = runTest {
        val chatPhoto = ChatTheme(aChatBackgroundPhoto(), ChatBubbleColor.Blue)
        val mediaPreviewConfig = MutableStateFlow(MediaPreviewConfig(mediaPreviewValue = MediaPreviewValue.Off, hideInviteAvatar = false))
        val presenter = createDefaultMessagesChatBackgroundPresenter(
            service = FakeChatBackgroundService(chatPhoto),
            setting = FakeChatThemeSetting(defaultTheme),
            joinRule = JoinRule.Public,
            mediaPreviewConfig = mediaPreviewConfig,
        )

        presenter.test {
            assertThat(awaitItem()).isEqualTo(defaultTheme)
            mediaPreviewConfig.value = MediaPreviewConfig(mediaPreviewValue = MediaPreviewValue.On, hideInviteAvatar = false)
            assertThat(awaitItem()).isEqualTo(chatPhoto)
        }
    }

    @Test
    fun `a chat photo in a private chat shows even with media off`() = runTest {
        val chatPhoto = ChatTheme(aChatBackgroundPhoto(), ChatBubbleColor.Blue)
        val presenter = createDefaultMessagesChatBackgroundPresenter(
            service = FakeChatBackgroundService(chatPhoto),
            setting = FakeChatThemeSetting(defaultTheme),
            joinRule = JoinRule.Invite,
            mediaPreviewConfig = MutableStateFlow(MediaPreviewConfig(mediaPreviewValue = MediaPreviewValue.Off, hideInviteAvatar = false)),
        )

        presenter.test {
            assertThat(awaitItem()).isEqualTo(chatPhoto)
        }
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

    private val defaultTheme = ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Sunset), ChatBubbleColor.Pink)

    private fun TestScope.createDefaultMessagesChatBackgroundPresenter(
        service: FakeChatBackgroundService,
        setting: FakeChatThemeSetting = FakeChatThemeSetting(),
        timeline: FakeTimeline = FakeTimeline(),
        joinRule: JoinRule = JoinRule.Public,
        mediaPreviewConfig: MutableStateFlow<MediaPreviewConfig> = MutableStateFlow(MediaPreviewConfig.DEFAULT),
    ): DefaultMessagesChatBackgroundPresenter {
        val room = FakeJoinedRoom(baseRoom = FakeBaseRoom(initialRoomInfo = aRoomInfo(joinRule = joinRule)), liveTimeline = timeline)
        return DefaultMessagesChatBackgroundPresenter(
            timelineController = TimelineController(
                room = room,
                liveTimeline = timeline,
                roomCoroutineScope = backgroundScope,
                dispatchers = testCoroutineDispatchers(),
            ),
            room = room,
            chatBackgroundService = service,
            chatThemeSetting = setting,
            mediaPreviewService = FakeMediaPreviewService(mediaPreviewConfig),
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
