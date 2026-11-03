/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.chatbackground.impl

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class ChatBackgroundViewTest : RobolectricTest() {
    @Test
    fun `clicking on back invokes the callback`() = runAndroidComposeUiTest {
        ensureCalledOnce {
            setChatBackgroundView(aChatBackgroundState(eventSink = EventsRecorder(expectEvents = false)), onBackClick = it)
            pressBack()
        }
    }

    @Test
    fun `clicking on a preset selects it`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<ChatBackgroundEvent>()
        setChatBackgroundView(aChatBackgroundState(eventSink = eventsRecorder))
        onNodeWithText(activity!!.getString(ChatBackgroundPreset.Mint.nameRes)).performClick()
        eventsRecorder.assertSingle(ChatBackgroundEvent.SelectPreset(ChatBackgroundPreset.Mint))
    }

    @Test
    fun `the current preset is shown selected`() = runAndroidComposeUiTest {
        setChatBackgroundView(aChatBackgroundState(current = ChatBackground.Preset(ChatBackgroundPreset.Sky)))
        onNodeWithText(activity!!.getString(ChatBackgroundPreset.Sky.nameRes)).assertIsSelected()
    }

    @Test
    fun `set does nothing until another background is picked`() = runAndroidComposeUiTest {
        setChatBackgroundView(aChatBackgroundState(eventSink = EventsRecorder(expectEvents = false)))
        clickOn(R.string.gua_chat_background_action_set)
    }

    @Test
    fun `set applies the picked background`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<ChatBackgroundEvent>()
        setChatBackgroundView(aChatBackgroundState(selection = ChatBackground.Preset(ChatBackgroundPreset.Night), eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_background_action_set)
        eventsRecorder.assertSingle(ChatBackgroundEvent.Apply)
    }

    @Test
    fun `remove removes the current background`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<ChatBackgroundEvent>()
        setChatBackgroundView(aChatBackgroundState(current = ChatBackground.Preset(ChatBackgroundPreset.Sand), eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_background_action_remove)
        eventsRecorder.assertSingle(ChatBackgroundEvent.Remove)
    }

    @Test
    fun `choose photo opens the photo picker`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<ChatBackgroundEvent>()
        setChatBackgroundView(aChatBackgroundState(eventSink = eventsRecorder))
        scrollToAndClick(R.string.gua_chat_background_choose_photo)
        eventsRecorder.assertSingle(ChatBackgroundEvent.ChoosePhoto)
    }

    @Test
    fun `without permission nothing can be picked`() = runAndroidComposeUiTest {
        setChatBackgroundView(aChatBackgroundState(canChange = false, eventSink = EventsRecorder(expectEvents = false)))
        onNodeWithText(activity!!.getString(ChatBackgroundPreset.Mint.nameRes)).performClick()
        scrollToAndClick(R.string.gua_chat_background_choose_photo)
    }

    @Test
    fun `a successful change closes the screen`() = runAndroidComposeUiTest {
        ensureCalledOnce {
            setChatBackgroundView(aChatBackgroundState(action = AsyncAction.Success(Unit)), onDone = it)
        }
    }

    @Test
    fun `the photo preview sets the photo, and back discards it`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<ChatBackgroundEvent>()
        setChatBackgroundView(aChatBackgroundState(photoDraft = aChatBackgroundPhotoDraft(), eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_background_preview_dark)
        clickOn(R.string.gua_chat_background_action_set)
        pressBack()
        eventsRecorder.assertList(
            listOf(ChatBackgroundEvent.SetDarkPreview(true), ChatBackgroundEvent.Apply, ChatBackgroundEvent.DiscardPhoto)
        )
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.scrollToAndClick(@StringRes res: Int) {
    onNode(hasText(activity!!.getString(res)) and hasClickAction()).performScrollTo().performClick()
}

private fun AndroidComposeUiTest<ComponentActivity>.setChatBackgroundView(
    state: ChatBackgroundState,
    onBackClick: () -> Unit = EnsureNeverCalled(),
    onDone: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        ChatBackgroundView(
            state = state,
            onBackClick = onBackClick,
            onDone = onDone,
        )
    }
}
