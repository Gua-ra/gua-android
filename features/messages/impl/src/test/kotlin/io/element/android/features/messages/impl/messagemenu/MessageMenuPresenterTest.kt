/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.aUserEventPermissions
import io.element.android.features.messages.impl.actionlist.ActionListPresenter
import io.element.android.features.messages.impl.actionlist.DefaultActionListPresenter
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.actionlist.model.TimelineItemActionPostProcessor
import io.element.android.features.messages.impl.crypto.sendfailure.VerifiedUserSendFailureFactory
import io.element.android.features.messages.impl.fixtures.aMessageEvent
import io.element.android.features.messages.impl.timeline.model.AggregatedReaction
import io.element.android.features.messages.impl.timeline.model.AggregatedReactionSender
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemReactions
import io.element.android.features.messages.impl.timeline.model.TimelineItemThreadInfo
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRedactedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemEncryptedContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.emoji.api.recentemojis.GetRecentEmojis
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.libraries.matrix.test.A_TRANSACTION_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.consumeItemsUntilTimeout
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MessageMenuPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val permissions = aUserEventPermissions(
        canRedactOwn = true,
        canRedactOther = false,
        canSendMessage = true,
        canSendReaction = true,
        canPinUnpin = true,
    )

    @Test
    fun `present - initial state`() = runTest {
        createMessageMenuPresenter().test {
            val initialState = awaitItem()
            assertThat(initialState.target).isEqualTo(MessageMenuState.Target.None)
            assertThat(initialState.selectableText).isNull()
        }
    }

    @Test
    fun `present - a long press on an outgoing text message shows the reactions and the actions`() = runTest {
        createMessageMenuPresenter().test {
            val event = aMessageEvent(isMine = true, isEditable = true)
            awaitItem().eventSink(MessageMenuEvent.Show(event, MessageMenuGesture.LongPress, permissions))

            val target = awaitShown()
            assertThat(target.event).isEqualTo(event)
            assertThat(target.gesture).isEqualTo(MessageMenuGesture.LongPress)
            assertThat(target.reactions).isEqualTo(MessageQuickReactions.defaults)
            assertThat(target.sections.primary).containsExactly(
                MessageMenuAction.Timeline(TimelineItemAction.Reply),
                MessageMenuAction.Timeline(TimelineItemAction.CopyText),
                MessageMenuAction.Select,
                MessageMenuAction.Timeline(TimelineItemAction.Edit),
                MessageMenuAction.Timeline(TimelineItemAction.Forward),
            ).inOrder()
            assertThat(target.sections.more).containsExactly(
                MessageMenuAction.Timeline(TimelineItemAction.Pin),
                MessageMenuAction.Timeline(TimelineItemAction.CopyLink),
            ).inOrder()
            assertThat(target.sections.destructive).containsExactly(MessageMenuAction.Timeline(TimelineItemAction.Redact))
            assertThat(target.reactionRows).isEmpty()
        }
    }

    @Test
    fun `present - a long press on an incoming message reports it under More`() = runTest {
        createMessageMenuPresenter().test {
            val event = aMessageEvent(isMine = false, isEditable = false)
            awaitItem().eventSink(MessageMenuEvent.Show(event, MessageMenuGesture.LongPress, permissions))

            val target = awaitShown()
            assertThat(target.sections.primary).containsExactly(
                MessageMenuAction.Timeline(TimelineItemAction.Reply),
                MessageMenuAction.Timeline(TimelineItemAction.CopyText),
                MessageMenuAction.Select,
                MessageMenuAction.Timeline(TimelineItemAction.Forward),
            ).inOrder()
            assertThat(target.sections.more.last()).isEqualTo(MessageMenuAction.Timeline(TimelineItemAction.ReportContent))
            assertThat(target.sections.destructive).isEmpty()
        }
    }

    @Test
    fun `present - a long press on media offers no Select`() = runTest {
        createMessageMenuPresenter().test {
            val event = aMessageEvent(isMine = true, content = aTimelineItemImageContent(caption = "Floripa"))
            awaitItem().eventSink(MessageMenuEvent.Show(event, MessageMenuGesture.LongPress, permissions))

            val target = awaitShown()
            assertThat(target.sections.primary).doesNotContain(MessageMenuAction.Select)
            assertThat(target.sections.primary).contains(MessageMenuAction.Timeline(TimelineItemAction.CopyCaption))
        }
    }

    @Test
    fun `present - a long press on a removed message opens nothing`() = runTest {
        createMessageMenuPresenter().test {
            val event = aMessageEvent(isMine = false, isEditable = false, canBeRepliedTo = false, content = TimelineItemRedactedContent)
            awaitItem().eventSink(MessageMenuEvent.Show(event, MessageMenuGesture.LongPress, permissions))

            val states = consumeItemsUntilTimeout()
            assertThat(states.map { it.target }.filterIsInstance<MessageMenuState.Target.Shown>()).isEmpty()
        }
    }

    @Test
    fun `present - a long press on a message that failed to send shows its actions without reactions`() = runTest {
        createMessageMenuPresenter().test {
            val event = aMessageEvent(eventId = null, transactionId = A_TRANSACTION_ID, sendState = LocalEventSendState.Failed.Unknown("error"))
            awaitItem().eventSink(MessageMenuEvent.Show(event, MessageMenuGesture.LongPress, permissions))

            val target = awaitShown()
            assertThat(target.reactions).isEmpty()
            assertThat(target.sections.primary).contains(MessageMenuAction.Timeline(TimelineItemAction.CopyText))
        }
    }

    @Test
    fun `present - a long press on a message that could not be decrypted shows its actions without reactions`() = runTest {
        createMessageMenuPresenter().test {
            val event = aMessageEvent(isMine = false, isEditable = false, content = aTimelineItemEncryptedContent())
            awaitItem().eventSink(MessageMenuEvent.Show(event, MessageMenuGesture.LongPress, permissions))

            val target = awaitShown()
            assertThat(target.reactions).isEmpty()
            assertThat(target.sections.isEmpty).isFalse()
        }
    }

    @Test
    fun `present - a long press on a reply inside a thread offers Reply in thread in the main list`() = runTest {
        createMessageMenuPresenter().test {
            val event = aMessageEvent(isMine = false, isEditable = false, threadInfo = TimelineItemThreadInfo.ThreadResponse(threadRootId = A_THREAD_ID))
            awaitItem().eventSink(MessageMenuEvent.Show(event, MessageMenuGesture.LongPress, permissions))

            val target = awaitShown()
            assertThat(target.sections.primary.first()).isEqualTo(MessageMenuAction.Timeline(TimelineItemAction.ReplyInThread))
            assertThat(target.sections.more).doesNotContain(MessageMenuAction.Timeline(TimelineItemAction.ReplyInThread))
        }
    }

    @Test
    fun `present - a double tap shows the reactions and who reacted, the current user first`() = runTest {
        val room = FakeBaseRoom().apply {
            givenRoomMembersState(
                RoomMembersState.Ready(persistentListOf(aRoomMember(userId = CAMILA, displayName = "Camila Moraes")))
            )
        }
        createMessageMenuPresenter(room = room).test {
            val event = aMessageEvent(isMine = false).withReactions(
                reaction("❤️", sender(CAMILA, 1), sender(A_USER_ID, 3)),
                reaction("😂", sender(RAFA, 2)),
            )
            awaitItem().eventSink(MessageMenuEvent.Show(event, MessageMenuGesture.DoubleTap, permissions))

            val target = consumeItemsUntilPredicate { state ->
                (state.target as? MessageMenuState.Target.Shown)?.reactionRows?.any { it.name == "Camila Moraes" } == true
            }.last().target as MessageMenuState.Target.Shown
            assertThat(target.gesture).isEqualTo(MessageMenuGesture.DoubleTap)
            assertThat(target.sections.isEmpty).isTrue()
            assertThat(target.reactions).isEqualTo(MessageQuickReactions.defaults)
            assertThat(target.highlightedReactions).containsExactly("❤️")
            assertThat(target.reactionRows.map { it.userId }).containsExactly(A_USER_ID, CAMILA, RAFA).inOrder()
            assertThat(target.reactionRows.map { it.isCurrentUser }).containsExactly(true, false, false).inOrder()
            assertThat(target.reactionRows.map { it.name }).containsExactly(A_USER_ID.displayHandle, "Camila Moraes", "@rafa").inOrder()
        }
    }

    @Test
    fun `present - a double tap opens nothing without permission to react`() = runTest {
        createMessageMenuPresenter().test {
            val event = aMessageEvent(isMine = false)
            awaitItem().eventSink(MessageMenuEvent.Show(event, MessageMenuGesture.DoubleTap, permissions.copy(canSendReaction = false)))

            val states = consumeItemsUntilTimeout()
            assertThat(states.map { it.target }.filterIsInstance<MessageMenuState.Target.Shown>()).isEmpty()
        }
    }

    @Test
    fun `present - frequently used emoji follow the Gua reactions`() = runTest {
        createMessageMenuPresenter(recentEmojis = listOf("🔥", "🙏")).test {
            awaitItem().eventSink(MessageMenuEvent.Show(aMessageEvent(), MessageMenuGesture.LongPress, permissions))

            assertThat(awaitShown().reactions).containsExactlyElementsIn(MessageQuickReactions.defaults + listOf("🔥", "🙏")).inOrder()
        }
    }

    @Test
    fun `present - dismiss closes the menu`() = runTest {
        createMessageMenuPresenter().test {
            awaitItem().eventSink(MessageMenuEvent.Show(aMessageEvent(), MessageMenuGesture.LongPress, permissions))
            consumeItemsUntilPredicate { it.target is MessageMenuState.Target.Shown }.last().eventSink(MessageMenuEvent.Dismiss)

            assertThat(consumeItemsUntilTimeout().last().target).isEqualTo(MessageMenuState.Target.None)
        }
    }

    @Test
    fun `present - select shows the message text until it is dismissed`() = runTest {
        createMessageMenuPresenter().test {
            val event = aMessageEvent(
                content = TimelineItemTextContent(body = "Que paz nesse lugar", htmlDocument = null, formattedBody = "Que paz nesse lugar", isEdited = false)
            )
            awaitItem().eventSink(MessageMenuEvent.SelectText(event))
            val withText = awaitItem()
            assertThat(withText.selectableText).isEqualTo("Que paz nesse lugar")

            withText.eventSink(MessageMenuEvent.DismissTextSelection)
            assertThat(awaitItem().selectableText).isNull()
        }
    }

    private suspend fun ReceiveTurbine<MessageMenuState>.awaitShown(): MessageMenuState.Target.Shown {
        return consumeItemsUntilPredicate { it.target is MessageMenuState.Target.Shown }.last().target as MessageMenuState.Target.Shown
    }

    private fun TimelineItem.Event.withReactions(vararg reactions: AggregatedReaction) = copy(
        reactionsState = TimelineItemReactions(reactions.toList().toImmutableList())
    )

    private fun reaction(key: String, vararg senders: AggregatedReactionSender) = AggregatedReaction(
        currentUserId = A_USER_ID,
        key = key,
        senders = senders.toList().toImmutableList(),
    )

    private fun sender(userId: UserId, minute: Long) = AggregatedReactionSender(senderId = userId, timestamp = minute * 60_000, sentTime = "")

    private fun createMessageMenuPresenter(
        room: BaseRoom = FakeBaseRoom(),
        recentEmojis: List<String> = emptyList(),
    ): MessageMenuPresenter {
        val actionListPresenterFactory = object : ActionListPresenter.Factory {
            override fun create(postProcessor: TimelineItemActionPostProcessor, timelineMode: Timeline.Mode) =
                DefaultActionListPresenter(
                    postProcessor = postProcessor,
                    timelineMode = timelineMode,
                    appPreferencesStore = InMemoryAppPreferencesStore(isDeveloperModeEnabled = false),
                    room = room,
                    userSendFailureFactory = VerifiedUserSendFailureFactory(room),
                    dateFormatter = FakeDateFormatter(),
                    featureFlagService = FakeFeatureFlagService(),
                    getRecentEmojis = GetRecentEmojis { Result.success(recentEmojis.toImmutableList()) },
                )
        }
        return DefaultMessageMenuPresenter(
            timelineMode = Timeline.Mode.Live,
            actionListPresenterFactory = actionListPresenterFactory,
            room = room,
            mentionSpanUpdater = FakeMentionSpanUpdater(),
        )
    }

    private companion object {
        val CAMILA = UserId("@camila:gua.global")
        val RAFA = UserId("@rafa:gua.global")
    }
}
