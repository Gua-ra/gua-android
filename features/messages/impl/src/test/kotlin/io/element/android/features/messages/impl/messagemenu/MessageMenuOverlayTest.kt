/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.messagemenu

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
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
import io.element.android.tests.testutils.pressBackKey
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
        onNodeWithText(string(CommonStrings.action_remove_message)).assertIsDisplayed()
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

    @Test
    fun `tapping the backdrop closes the menu`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<MessageMenuEvent>()
        setOverlay(aLongPressTarget(), eventSink = eventsRecorder)

        onRoot().performTouchInput { click(Offset(10f, 10f)) }
        mainClock.advanceTimeBy(1_000)
        eventsRecorder.assertSingle(MessageMenuEvent.Dismiss)
    }

    @Test
    fun `back closes the menu`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<MessageMenuEvent>()
        setOverlay(aLongPressTarget(), eventSink = eventsRecorder)

        pressBackKey()
        mainClock.advanceTimeBy(1_000)
        eventsRecorder.assertSingle(MessageMenuEvent.Dismiss)
    }

    @Test
    fun `a configuration change closes the menu`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<MessageMenuEvent>()
        val configuration = mutableStateOf<Configuration?>(null)
        setOverlay(aLongPressTarget(), eventSink = eventsRecorder, configuration = configuration)

        runOnIdle {
            configuration.value = Configuration(requireNotNull(activity).resources.configuration).apply { screenWidthDp += 200 }
        }
        waitForIdle()
        eventsRecorder.assertSingle(MessageMenuEvent.Dismiss)
    }

    @Test
    fun `holding content that handles its own presses lifts the bubble until it is let go`() = runAndroidComposeUiTest {
        val controller = setBubbleWithOwnPressHandling()

        onNodeWithTag(MEDIA_TAG).performTouchInput { down(center) }
        mainClock.advanceTimeBy(150)
        assertThat(controller.pressScaleOf(event.id)).isEqualTo(1f)
        mainClock.advanceTimeBy(450)
        assertThat(controller.pressScaleOf(event.id)).isGreaterThan(1f)

        onNodeWithTag(MEDIA_TAG).performTouchInput { up() }
        mainClock.advanceTimeBy(1_000)
        assertThat(controller.pressScaleOf(event.id)).isEqualTo(1f)
    }

    @Test
    fun `a quick tap does not lift the bubble`() = runAndroidComposeUiTest {
        val controller = setBubbleWithOwnPressHandling()

        onNodeWithTag(MEDIA_TAG).performTouchInput { down(center) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(MEDIA_TAG).performTouchInput { up() }
        mainClock.advanceTimeBy(600)
        assertThat(controller.pressScaleOf(event.id)).isEqualTo(1f)
    }

    @Test
    fun `dragging away from the bubble does not lift it`() = runAndroidComposeUiTest {
        val controller = setBubbleWithOwnPressHandling()

        onNodeWithTag(MEDIA_TAG).performTouchInput { down(center) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(MEDIA_TAG).performTouchInput { moveBy(Offset(0f, 2_000f)) }
        mainClock.advanceTimeBy(600)
        assertThat(controller.pressScaleOf(event.id)).isEqualTo(1f)
        onNodeWithTag(MEDIA_TAG).performTouchInput { up() }
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
        configuration: MutableState<Configuration?> = mutableStateOf(null),
    ) {
        setSafeContent {
            val controller = rememberMessageMenuController()
            MessageMenuHost(
                controller = controller,
                modifier = Modifier.fillMaxSize(),
                overlay = {
                    CompositionLocalProvider(LocalConfiguration provides (configuration.value ?: LocalConfiguration.current)) {
                        MessageMenuOverlay(
                            state = aMessageMenuState(target = target, eventSink = eventSink),
                            controller = controller,
                            onReactionClick = onReactionClick,
                            onAddReactionClick = onAddReactionClick,
                            onActionClick = onActionClick,
                            onSelectClick = onSelectClick,
                        )
                    }
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
                                liftsWhileHeld = true,
                            )
                    )
                }
            }
        }
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setBubbleWithOwnPressHandling(): MessageMenuController {
        lateinit var controller: MessageMenuController
        setSafeContent {
            controller = rememberMessageMenuController()
            MessageMenuHost(
                controller = controller,
                modifier = Modifier.fillMaxSize(),
                overlay = {},
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 240.dp)
                        .size(width = 240.dp, height = 160.dp)
                        .messageMenuBubble(id = event.id, controller = controller, liftsWhileHeld = true)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(MEDIA_TAG)
                            .combinedClickable(onClick = {}, onLongClick = {})
                    )
                }
            }
        }
        mainClock.autoAdvance = false
        return controller
    }

    private companion object {
        const val MEDIA_TAG = "media"
    }
}
