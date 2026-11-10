/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.chatbackground.impl

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.Config
import io.element.android.libraries.chatbackground.api.R as ChatBackgroundR

@Config(qualifiers = "h1500dp")
class ChatThemeViewTest : RobolectricTest() {
    private val oceanTeal = ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Ocean), ChatBubbleColor.Teal)

    @Test
    fun `clicking on back invokes the callback`() = runAndroidComposeUiTest<ComponentActivity> {
        ensureCalledOnce {
            setChatThemeView(aChatThemeState(eventSink = EventsRecorder(expectEvents = false)), onBackClick = it)
            pressBack()
        }
    }

    @Test
    fun `the round buttons open the bubble and wallpaper pickers`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_theme_chat_bubble)
        clickOn(R.string.gua_chat_theme_wallpaper)
        eventsRecorder.assertList(listOf(ChatThemeEvent.OpenBubblePicker, ChatThemeEvent.OpenWallpaperPicker))
    }

    @Test
    fun `clicking on a tile selects its theme`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(eventSink = eventsRecorder))
        onAllNodesWithText(activity!!.getString(ChatBackgroundPreset.Ocean.nameRes))[0].performClick()
        eventsRecorder.assertSingle(ChatThemeEvent.SelectTheme(oceanTeal))
    }

    @Test
    fun `in a chat the first tile is Default, selected while the chat has no theme`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(eventSink = eventsRecorder))
        val defaultTile = onNodeWithText(activity!!.getString(ChatBackgroundR.string.gua_chat_background_value_default))
        defaultTile.assertIsSelected()
        defaultTile.performClick()
        eventsRecorder.assertSingle(ChatThemeEvent.SelectDefault)
        onNodeWithText(activity!!.getString(R.string.gua_chat_theme_chat_note)).assertExists()
    }

    @Test
    fun `for the default the first tile is Classic, and the note says where it applies`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(mode = ChatThemeMode.Default, current = oceanTeal))
        onNodeWithText(activity!!.getString(R.string.gua_chat_theme_classic)).assertIsNotSelected()
        onAllNodesWithText(activity!!.getString(ChatBackgroundPreset.Ocean.nameRes))[0].assertIsSelected()
        onNode(hasText(activity!!.getString(R.string.gua_chat_theme_default_note))).performScrollTo()
    }

    @Test
    fun `without permission nothing can be chosen and the reason shows`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(current = oceanTeal, hasPermission = false, eventSink = EventsRecorder(expectEvents = false)))
        clickOn(R.string.gua_chat_theme_chat_bubble)
        clickOn(R.string.gua_chat_theme_wallpaper)
        onAllNodesWithText(activity!!.getString(ChatBackgroundPreset.Ocean.nameRes))[0].performClick()
        onNode(hasText(activity!!.getString(R.string.gua_chat_theme_no_permission))).performScrollTo()
    }

    @Test
    fun `while the permissions load nothing can be chosen and no reason shows`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(current = oceanTeal, hasPermission = null, eventSink = EventsRecorder(expectEvents = false)))
        clickOn(R.string.gua_chat_theme_chat_bubble)
        clickOn(R.string.gua_chat_theme_wallpaper)
        onNodeWithText(activity!!.getString(R.string.gua_chat_theme_no_permission)).assertDoesNotExist()
    }

    @Test
    fun `the preview sets the theme, cancels, and back cancels too`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(preview = ChatThemePreview(oceanTeal, oceanTeal), eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_background_preview_dark)
        clickOn(R.string.gua_chat_theme_action_set_theme)
        clickOn(CommonStrings.action_cancel)
        pressBack()
        eventsRecorder.assertList(
            listOf(ChatThemeEvent.SetDarkPreview(true), ChatThemeEvent.Save, ChatThemeEvent.Cancel, ChatThemeEvent.Cancel)
        )
    }

    @Test
    fun `the preview of the default says Set default`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(mode = ChatThemeMode.Default, preview = ChatThemePreview(oceanTeal, oceanTeal), eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_theme_action_set_default)
        eventsRecorder.assertSingle(ChatThemeEvent.Save)
    }

    @Test
    fun `the bubble picker selects a colour and sets it`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(
            aChatThemeState(
                current = oceanTeal,
                page = ChatThemePage.BubblePicker(selection = ChatBubbleColor.Blue),
                eventSink = eventsRecorder,
            )
        )
        val context: Context = activity!!
        onNodeWithContentDescription(context.getString(ChatBubbleColor.Blue.nameRes)).assertIsSelected()
        onNodeWithContentDescription(context.getString(R.string.gua_chat_theme_bubble_standard)).performClick()
        onNodeWithContentDescription(context.getString(ChatBubbleColor.Pink.nameRes)).performClick()
        clickOn(R.string.gua_chat_theme_action_set)
        pressBack()
        eventsRecorder.assertList(
            listOf(
                ChatThemeEvent.SelectBubble(null),
                ChatThemeEvent.SelectBubble(ChatBubbleColor.Pink),
                ChatThemeEvent.Save,
                ChatThemeEvent.Cancel,
            )
        )
    }

    @Test
    fun `the bubble picker cannot set the bubble the theme already has`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(
            aChatThemeState(
                current = oceanTeal,
                page = ChatThemePage.BubblePicker(selection = ChatBubbleColor.Teal),
                eventSink = EventsRecorder(expectEvents = false),
            )
        )
        clickOn(R.string.gua_chat_theme_action_set)
    }

    @Test
    fun `the wallpaper picker offers a photo, no wallpaper and the wallpapers`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(current = oceanTeal, page = ChatThemePage.WallpaperPicker, eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_theme_choose_photo)
        clickOn(R.string.gua_chat_theme_no_wallpaper)
        onNodeWithText(activity!!.getString(ChatBackgroundPreset.Ocean.nameRes)).assertIsSelected()
        scrollToAndClick(ChatBackgroundPreset.Mint.nameRes)
        pressBack()
        eventsRecorder.assertList(
            listOf(
                ChatThemeEvent.ChoosePhoto,
                ChatThemeEvent.SelectWallpaper(ChatBackground.None),
                ChatThemeEvent.SelectWallpaper(ChatBackground.Preset(ChatBackgroundPreset.Mint)),
                ChatThemeEvent.Cancel,
            )
        )
    }

    @Test
    fun `the photo preview saves the photo, and back cancels it`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(photoDraft = aChatBackgroundPhotoDraft(), eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_background_preview_dark)
        clickOn(R.string.gua_chat_theme_action_set_theme)
        pressBack()
        eventsRecorder.assertList(
            listOf(ChatThemeEvent.SetDarkPreview(true), ChatThemeEvent.Save, ChatThemeEvent.Cancel)
        )
    }

    @Test
    fun `the four move actions move the photo the way they say, and the drag hint is read`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(photoDraft = aChatBackgroundPhotoDraft(), eventSink = eventsRecorder))
        val context: Context = activity!!
        val frame = onNodeWithContentDescription(
            "${context.getString(R.string.gua_chat_background_preview_a11y)}, ${context.getString(R.string.gua_chat_background_position_hint)}"
        )
        listOf(
            R.string.gua_chat_background_a11y_move_left,
            R.string.gua_chat_background_a11y_move_right,
            R.string.gua_chat_background_a11y_move_up,
            R.string.gua_chat_background_a11y_move_down,
        ).forEach { label ->
            frame.performCustomAccessibilityActionWithLabel(context.getString(label))
        }
        val center = ChatBackground.Photo.DEFAULT_ALIGN
        eventsRecorder.assertList(
            listOf(
                ChatThemeEvent.SetPhotoAlign(center + 100, center),
                ChatThemeEvent.SetPhotoAlign(center - 100, center),
                ChatThemeEvent.SetPhotoAlign(center, center + 100),
                ChatThemeEvent.SetPhotoAlign(center, center - 100),
            )
        )
    }

    @Test
    fun `without permission the photo preview cannot save the photo`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(
            aChatThemeState(photoDraft = aChatBackgroundPhotoDraft(), hasPermission = false, eventSink = EventsRecorder(expectEvents = false))
        )
        clickOn(R.string.gua_chat_theme_action_set_theme)
    }

    @Test
    fun `a failed save shows the existing error and dismissing it clears it`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(action = AsyncAction.Failure(IllegalStateException("403")), eventSink = eventsRecorder))
        onNodeWithText(activity!!.getString(R.string.gua_chat_background_error_generic)).assertExists()
        clickOn(CommonStrings.action_ok)
        eventsRecorder.assertSingle(ChatThemeEvent.ClearError)
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.scrollToAndClick(@StringRes res: Int) {
    onNode(hasText(activity!!.getString(res)) and hasClickAction()).performScrollTo().performClick()
}

private fun AndroidComposeUiTest<ComponentActivity>.setChatThemeView(
    state: ChatThemeState,
    onBackClick: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        ChatThemeView(
            state = state,
            onBackClick = onBackClick,
        )
    }
}
