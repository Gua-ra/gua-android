/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.messagemenu

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.fixtures.aMessageEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalledWithParam
import io.element.android.tests.testutils.EnsureNeverCalledWithTwoParams
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.assertNoNodeWithText
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnceWithParam
import io.element.android.tests.testutils.ensureCalledOnceWithTwoParams
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.setSafeContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class MessageMenuOverlayTest : RobolectricTest() {
    private val event = aMessageEvent(isMine = false)

    @Test
    fun `a long press shows the reactions and the lean action list`() = runAndroidComposeUiTest {
        setOverlay(aLongPressTarget())

        onNodeWithText(string(CommonStrings.action_reply)).assertIsDisplayed()
        onNodeWithText(string(CommonStrings.action_copy_text)).assertIsDisplayed()
        onNodeWithText(string(R.string.gua_action_select)).assertIsDisplayed()
        onNodeWithText(string(R.string.gua_action_more)).assertIsDisplayed()
        onNodeWithText(string(CommonStrings.action_remove)).assertIsDisplayed()
        onNodeWithText(MessageQuickReactions.defaults.first()).assertIsDisplayed()
        assertNoNodeWithText(CommonStrings.action_pin)
    }

    @Test
    fun `More replaces the list in place, and its back row returns`() = runAndroidComposeUiTest {
        setOverlay(aLongPressTarget())

        clickOn(R.string.gua_action_more)
        onNodeWithText(string(CommonStrings.action_pin)).assertIsDisplayed()
        onNodeWithText(string(CommonStrings.action_copy_link_to_message)).assertIsDisplayed()
        assertNoNodeWithText(CommonStrings.action_reply)

        clickOn(R.string.gua_action_more)
        onNodeWithText(string(CommonStrings.action_reply)).assertIsDisplayed()
    }

    @Test
    fun `choosing an action closes the menu and runs it`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<MessageMenuEvent>()
        ensureCalledOnceWithTwoParams(TimelineItemAction.CopyText, event) { callback ->
            setOverlay(aLongPressTarget(), eventSink = eventsRecorder, onActionClick = callback)
            clickOn(CommonStrings.action_copy_text)
            mainClock.advanceTimeBy(1_000)
        }
        eventsRecorder.assertSingle(MessageMenuEvent.Dismiss)
    }

    @Test
    fun `choosing Select closes the menu and asks for the text`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<MessageMenuEvent>()
        ensureCalledOnceWithParam(event) { callback ->
            setOverlay(aLongPressTarget(), eventSink = eventsRecorder, onSelectClick = callback)
            clickOn(R.string.gua_action_select)
            mainClock.advanceTimeBy(1_000)
        }
        eventsRecorder.assertSingle(MessageMenuEvent.Dismiss)
    }

    @Test
    fun `tapping a reaction toggles it and closes the menu`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<MessageMenuEvent>()
        val heart = MessageQuickReactions.defaults.first()
        ensureCalledOnceWithTwoParams(heart, event) { callback ->
            setOverlay(aLongPressTarget(), eventSink = eventsRecorder, onReactionClick = callback)
            onNodeWithText(heart).performClick()
            mainClock.advanceTimeBy(1_000)
        }
        eventsRecorder.assertSingle(MessageMenuEvent.Dismiss)
    }

    @Test
    fun `the add reaction button opens the emoji picker`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam(event) { callback ->
            setOverlay(aLongPressTarget(), onAddReactionClick = callback)
            onNodeWithContentDescription(string(CommonStrings.a11y_react_with_other_emojis)).performClick()
            mainClock.advanceTimeBy(1_000)
        }
    }

    @Test
    fun `a double tap lists who reacted, the current user as You`() = runAndroidComposeUiTest {
        setOverlay(
            MessageMenuState.Target.Shown(
                event = event,
                gesture = MessageMenuGesture.DoubleTap,
                reactions = MessageQuickReactions.defaults,
                highlightedReactions = persistentListOf(MessageQuickReactions.defaults.first()),
                sections = MessageMenuSections.Empty,
                reactionRows = persistentListOf(
                    aReactionRow(UserId("@me:gua.global"), isCurrentUser = true, name = "@me"),
                    aReactionRow(UserId("@camila:gua.global"), isCurrentUser = false, name = "Camila Moraes"),
                ),
            )
        )

        onNodeWithText(string(CommonStrings.common_you)).assertIsDisplayed()
        onNodeWithText("Camila Moraes").assertIsDisplayed()
        assertNoNodeWithText(CommonStrings.action_reply)
    }

    private fun AndroidComposeUiTest<ComponentActivity>.string(@StringRes id: Int): String = requireNotNull(activity).getString(id)

    private fun aLongPressTarget() = MessageMenuState.Target.Shown(
        event = event,
        gesture = MessageMenuGesture.LongPress,
        reactions = MessageQuickReactions.defaults,
        highlightedReactions = persistentListOf(),
        sections = MessageMenuSections.from(
            listOf(
                TimelineItemAction.Reply,
                TimelineItemAction.Forward,
                TimelineItemAction.Pin,
                TimelineItemAction.CopyText,
                TimelineItemAction.CopyLink,
                TimelineItemAction.Redact,
            )
        ),
        reactionRows = persistentListOf(),
    )

    private fun aReactionRow(userId: UserId, isCurrentUser: Boolean, name: String) = MessageReactionRow(
        userId = userId,
        isCurrentUser = isCurrentUser,
        name = name,
        avatarData = AvatarData(id = userId.value, name = name, size = AvatarSize.TimelineSender),
        keys = persistentListOf(MessageQuickReactions.defaults.first()),
    )

    private fun AndroidComposeUiTest<ComponentActivity>.setOverlay(
        target: MessageMenuState.Target,
        eventSink: (MessageMenuEvent) -> Unit = {},
        onReactionClick: (String, TimelineItem.Event) -> Unit = EnsureNeverCalledWithTwoParams(),
        onAddReactionClick: (TimelineItem.Event) -> Unit = EnsureNeverCalledWithParam(),
        onActionClick: (TimelineItemAction, TimelineItem.Event) -> Unit = EnsureNeverCalledWithTwoParams(),
        onSelectClick: (TimelineItem.Event) -> Unit = EnsureNeverCalledWithParam(),
    ) {
        setSafeContent {
            val controller = rememberMessageMenuController()
            MessageMenuHost(
                controller = controller,
                modifier = Modifier.fillMaxSize(),
                overlay = {
                    MessageMenuOverlay(
                        state = aMessageMenuState(target = target, eventSink = eventSink),
                        controller = controller,
                        onReactionClick = onReactionClick,
                        onAddReactionClick = onAddReactionClick,
                        onActionClick = onActionClick,
                        onSelectClick = onSelectClick,
                    )
                },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 240.dp)
                            .size(width = 240.dp, height = 60.dp)
                            .messageMenuBubble(
                                id = event.id,
                                controller = controller,
                                interactionSource = remember { MutableInteractionSource() },
                                liftsWhileHeld = true,
                            )
                    )
                }
            }
        }
    }
}
