/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatbackground

import com.google.common.truth.Truth.assertThat
import io.element.android.features.location.test.FakeActiveLiveLocationShareManager
import io.element.android.features.messages.impl.FakeMessagesNavigator
import io.element.android.features.messages.impl.crypto.sendfailure.resolve.aResolveVerifiedUserSendFailureState
import io.element.android.features.messages.impl.fixtures.aTimelineItemsFactoryCreator
import io.element.android.features.messages.impl.timeline.FakeMarkAsFullyRead
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.features.messages.impl.timeline.TimelineItemIndexer
import io.element.android.features.messages.impl.timeline.TimelinePresenter
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.protection.aTimelineProtectionState
import io.element.android.features.messages.impl.typing.aTypingNotificationState
import io.element.android.features.messages.impl.voicemessages.timeline.FakeRedactedVoiceMessageManager
import io.element.android.features.poll.test.actions.FakeEndPollAction
import io.element.android.features.poll.test.actions.FakeSendPollResponseAction
import io.element.android.features.roomcall.api.aStandByCallState
import io.element.android.libraries.androidutils.clipboard.FakeClipboardHelper
import io.element.android.libraries.androidutils.toast.FakeToastHelper
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.TransactionId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.EventReaction
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.api.timeline.item.event.ReactionSender
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aMessageContent
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import io.element.android.libraries.preferences.test.InMemorySessionPreferencesStore
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TimelinePresenterOwnNewEventCountTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val historyFromThisDevice = MatrixTimelineItem.Event(
        UniqueId("history"),
        anEventTimelineItem(eventId = EventId("\$history"), content = aMessageContent(), isOwn = true, timestamp = 1L),
    )

    @Test
    fun `present - the timeline opens with no sends counted`() = runTest {
        val timelineItems = MutableStateFlow(listOf<MatrixTimelineItem>(historyFromThisDevice))
        val presenter = createTimelinePresenter(FakeTimeline(timelineItems = timelineItems))
        presenter.test {
            val state = consumeItemsUntilPredicate { it.timelineItems.size == 1 }.last()
            assertThat(state.ownNewEventCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - each message the user sends from this device counts once`() = runTest {
        val timelineItems = MutableStateFlow(listOf<MatrixTimelineItem>(historyFromThisDevice))
        val presenter = createTimelinePresenter(FakeTimeline(timelineItems = timelineItems))
        val sentAt = System.currentTimeMillis()
        presenter.test {
            consumeItemsUntilPredicate { it.timelineItems.size == 1 }
            timelineItems.getAndUpdate { items -> listOf(aLocalEcho("first", sentAt)) + items }
            assertThat(consumeItemsUntilPredicate { it.ownNewEventCount == 1 }.last().ownNewEventCount).isEqualTo(1)
            // The echo becomes the remote event and gets a reaction: still one send.
            timelineItems.getAndUpdate { items ->
                listOf(aRemoteEcho("first", sentAt, reactions = persistentListOf(aReaction()))) + items.drop(1)
            }
            consumeItemsUntilPredicate { it.timelineItems.size == 2 && it.timelineItems.none { item -> item.isLocalEcho() } }
            timelineItems.getAndUpdate { items -> listOf(aLocalEcho("second", sentAt + 1)) + items }
            assertThat(consumeItemsUntilPredicate { it.ownNewEventCount == 2 }.last().ownNewEventCount).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - own events from another device and other people's messages count nothing`() = runTest {
        val timelineItems = MutableStateFlow(listOf<MatrixTimelineItem>(historyFromThisDevice))
        val presenter = createTimelinePresenter(FakeTimeline(timelineItems = timelineItems))
        val now = System.currentTimeMillis()
        presenter.test {
            consumeItemsUntilPredicate { it.timelineItems.size == 1 }
            timelineItems.getAndUpdate { items ->
                listOf(
                    MatrixTimelineItem.Event(
                        UniqueId("other-device"),
                        anEventTimelineItem(eventId = EventId("\$other-device"), content = aMessageContent(), isOwn = true, timestamp = now),
                    ),
                    MatrixTimelineItem.Event(
                        UniqueId("someone-else"),
                        anEventTimelineItem(eventId = EventId("\$someone-else"), content = aMessageContent(), timestamp = now),
                    ),
                ) + items
            }
            val state = consumeItemsUntilPredicate { it.timelineItems.size == 3 }.last()
            assertThat(state.ownNewEventCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun aLocalEcho(transactionId: String, sentAt: Long) = MatrixTimelineItem.Event(
        UniqueId("item-$transactionId"),
        anEventTimelineItem(
            transactionId = TransactionId(transactionId),
            content = aMessageContent(),
            isOwn = true,
            localSendState = LocalEventSendState.Sending.Event,
            timestamp = sentAt,
        ),
    )

    private fun aRemoteEcho(transactionId: String, sentAt: Long, reactions: ImmutableList<EventReaction>) =
        MatrixTimelineItem.Event(
            UniqueId("item-$transactionId"),
            anEventTimelineItem(
                eventId = EventId("\$remote-$transactionId"),
                content = aMessageContent(),
                isOwn = true,
                isRemote = true,
                reactions = reactions,
                timestamp = sentAt,
            ),
        )

    private fun aReaction() = EventReaction(key = "👍", senders = persistentListOf(ReactionSender(senderId = A_USER_ID, timestamp = 0L)))

    private fun TimelineItem.isLocalEcho() = this is TimelineItem.Event && transactionId != null

    private fun TestScope.createTimelinePresenter(timeline: Timeline): TimelinePresenter {
        val room = FakeJoinedRoom(liveTimeline = timeline, baseRoom = FakeBaseRoom(roomPermissions = FakeRoomPermissions()))
        return TimelinePresenter(
            timelineItemsFactoryCreator = aTimelineItemsFactoryCreator(),
            room = room,
            dispatchers = testCoroutineDispatchers(),
            sessionCoroutineScope = this,
            navigator = FakeMessagesNavigator(),
            redactedVoiceMessageManager = FakeRedactedVoiceMessageManager(),
            endPollAction = FakeEndPollAction(),
            sendPollResponseAction = FakeSendPollResponseAction(),
            sessionPreferencesStore = InMemorySessionPreferencesStore(),
            timelineItemIndexer = TimelineItemIndexer(),
            timelineController = TimelineController(
                room = room,
                liveTimeline = timeline,
                roomCoroutineScope = backgroundScope,
                dispatchers = testCoroutineDispatchers(),
            ),
            resolveVerifiedUserSendFailurePresenter = { aResolveVerifiedUserSendFailureState() },
            typingNotificationPresenter = { aTypingNotificationState() },
            roomCallStatePresenter = { aStandByCallState() },
            featureFlagService = FakeFeatureFlagService(),
            snackbarDispatcher = SnackbarDispatcher(),
            analyticsService = FakeAnalyticsService(),
            liveLocationShareManager = FakeActiveLiveLocationShareManager(),
            markAsFullyRead = FakeMarkAsFullyRead { _, _ -> },
            timelineProtectionPresenter = { aTimelineProtectionState() },
            clipboardHelper = FakeClipboardHelper(),
            toastHelper = FakeToastHelper(),
        )
    }
}
