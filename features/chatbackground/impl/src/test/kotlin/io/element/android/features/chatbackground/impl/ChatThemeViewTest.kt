/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.chatbackground.impl

import android.content.Context
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.height
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.Theme
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.matrix.ui.chatbackground.LiveWallpaperCues
import io.element.android.libraries.matrix.ui.chatbackground.LiveWallpaperCuesKey
import io.element.android.libraries.matrix.ui.chatbackground.aChatBackgroundPhoto
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import io.element.android.libraries.chatbackground.api.R as ChatBackgroundR

@Config(qualifiers = "w411dp-h1500dp")
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
    fun `the round buttons tell the current bubble and wallpaper`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(current = oceanTeal))
        val context: Context = activity!!
        onNodeWithText(context.getString(R.string.gua_chat_theme_chat_bubble)).assert(hasStateDescription(context.getString(ChatBubbleColor.Teal.nameRes)))
        onNodeWithText(context.getString(R.string.gua_chat_theme_wallpaper)).assert(hasStateDescription(context.getString(ChatBackgroundPreset.Ocean.nameRes)))
    }

    @Test
    fun `without a theme the round buttons tell the standard bubble and no wallpaper`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState())
        val context: Context = activity!!
        onNodeWithText(context.getString(R.string.gua_chat_theme_chat_bubble))
            .assert(hasStateDescription(context.getString(R.string.gua_chat_theme_bubble_standard)))
        onNodeWithText(context.getString(R.string.gua_chat_theme_wallpaper))
            .assert(hasStateDescription(context.getString(R.string.gua_chat_theme_no_wallpaper)))
    }

    @Test
    fun `a chat photo hidden by the media setting is still named on the wallpaper button`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(current = ChatTheme(aChatBackgroundPhoto(), ChatBubbleColor.Blue), isPhotoHidden = true))
        val context: Context = activity!!
        onNodeWithText(context.getString(R.string.gua_chat_theme_wallpaper))
            .assert(hasStateDescription(context.getString(ChatBackgroundR.string.gua_chat_background_value_photo)))
    }

    @Test
    fun `clicking on a tile selects its theme`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(eventSink = eventsRecorder))
        val silk = activity!!.getString(ChatArtwork.Silk.nameRes)
        onNodeWithText(silk).assertDoesNotExist()
        onAllNodesWithContentDescription(silk, substring = true)[0].performClick()
        eventsRecorder.assertSingle(ChatThemeEvent.SelectTheme(ChatArtwork.Silk.theme))
    }

    @Test
    fun `a tile is selected only while the theme has its wallpaper and its bubble pair`() = runAndroidComposeUiTest<ComponentActivity> {
        var current by mutableStateOf(ChatArtwork.GlassDrops.theme)
        setContent { ChatThemeView(state = aChatThemeState(current = current), onBackClick = EnsureNeverCalled()) }
        val glassDrops = activity!!.getString(ChatArtwork.GlassDrops.nameRes)
        val tiles = onAllNodesWithContentDescription("$glassDrops,", substring = true)
        tiles.assertCountEquals(2)
        tiles[0].assertIsSelected()
        tiles[1].assertIsSelected()
        current = ChatTheme(ChatBackground.Art(ChatArtwork.GlassDrops), ChatBubbleColor.Purple)
        tiles[0].assertIsNotSelected()
        tiles[1].assertIsNotSelected()
    }

    @Test
    fun `live tiles say they are live, the others do not`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState())
        val context: Context = activity!!
        val live = context.getString(R.string.gua_chat_theme_live_badge)
        onAllNodesWithContentDescription("${context.getString(ChatArtwork.GlassDrops.nameRes)}, $live", substring = true).assertCountEquals(2)
        onAllNodesWithContentDescription("${context.getString(ChatArtwork.PalmCoast.nameRes)}, $live", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a row shows the tiles on screen and scrolls to the others`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(eventSink = eventsRecorder))
        val context: Context = activity!!
        val citrus = hasContentDescription("${context.getString(ChatBackgroundPreset.Citrus.nameRes)},", substring = true)
        onNode(citrus).assertDoesNotExist()
        // The gradients, the last row, end with Citrus.
        onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(citrus)
        onNode(citrus).performClick()
        eventsRecorder.assertSingle(ChatThemeEvent.SelectTheme(ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Citrus), ChatBubbleColor.Green)))
    }

    @Test
    fun `a row keeps its height when its labelled tile scrolls out of view`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState())
        val featured = onAllNodes(hasScrollToIndexAction()).onFirst()
        val height = featured.getUnclippedBoundsInRoot().height
        featured.performScrollToIndex(ChatThemeSection.Featured.themes.size)
        onNodeWithContentDescription("${activity!!.getString(ChatBackgroundR.string.gua_chat_background_value_default)},", substring = true)
            .assertDoesNotExist()
        featured.assertHeightIsEqualTo(height)
    }

    @Test
    fun `in dark appearance the tiles and the bubble button tell the dark bubble of a pair`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(current = ChatArtwork.GlassDrops.theme), isDark = true)
        val context: Context = activity!!
        onNodeWithText(context.getString(R.string.gua_chat_theme_chat_bubble)).assert(hasStateDescription(context.getString(ChatBubbleColor.Pink.nameRes)))
        val pinkBubble = context.getString(R.string.gua_chat_theme_bubble_a11y, context.getString(ChatBubbleColor.Pink.nameRes))
        onAllNodesWithContentDescription("${context.getString(ChatArtwork.GlassDrops.nameRes)}, ", substring = true)[0]
            .assert(hasContentDescription(pinkBubble, substring = true))
    }

    @Test
    fun `in a chat the first tile is Default, selected while the chat has no theme`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(eventSink = eventsRecorder))
        val defaultTile = onNodeWithContentDescription("${activity!!.getString(ChatBackgroundR.string.gua_chat_background_value_default)},", substring = true)
        defaultTile.assertIsSelected()
        defaultTile.performClick()
        eventsRecorder.assertSingle(ChatThemeEvent.SelectDefault)
        onNodeWithText(activity!!.getString(R.string.gua_chat_theme_chat_note)).assertExists()
    }

    @Test
    fun `for the default the first tile is Classic, and the note says where it applies`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(mode = ChatThemeMode.Default, current = ChatArtwork.Silk.theme))
        onNodeWithContentDescription("${activity!!.getString(R.string.gua_chat_theme_classic)},", substring = true).assertIsNotSelected()
        onAllNodesWithContentDescription(activity!!.getString(ChatArtwork.Silk.nameRes), substring = true)[0].assertIsSelected()
        onNode(hasText(activity!!.getString(R.string.gua_chat_theme_default_note))).performScrollTo()
    }

    @Test
    fun `without permission nothing can be chosen and the reason shows`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(current = oceanTeal, hasPermission = false, eventSink = EventsRecorder(expectEvents = false)))
        clickOn(R.string.gua_chat_theme_chat_bubble)
        clickOn(R.string.gua_chat_theme_wallpaper)
        onAllNodesWithContentDescription(activity!!.getString(ChatArtwork.Silk.nameRes), substring = true)[0].performClick()
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
    fun `the preview cannot set the theme the chat already has`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(
            aChatThemeState(current = oceanTeal, preview = ChatThemePreview(oceanTeal, oceanTeal), eventSink = EventsRecorder(expectEvents = false))
        )
        clickOn(R.string.gua_chat_theme_action_set_theme)
    }

    @Test
    fun `the preview of the default says Set default`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(mode = ChatThemeMode.Default, preview = ChatThemePreview(oceanTeal, oceanTeal), eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_theme_action_set_default)
        eventsRecorder.assertSingle(ChatThemeEvent.Save)
    }

    @Test
    fun `a wallpaper chosen from the picker is previewed with Set`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(
            aChatThemeState(page = ChatThemePage.WallpaperPicker, preview = ChatThemePreview(oceanTeal, oceanTeal), eventSink = eventsRecorder)
        )
        clickOn(R.string.gua_chat_theme_action_set)
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
    fun `the bubble picker shows the pair's bubble per appearance and keeps the pair`() = runAndroidComposeUiTest<ComponentActivity> {
        var isDark by mutableStateOf(false)
        setContent {
            ElementTheme(theme = if (isDark) Theme.Dark else Theme.Light, applySystemBarsUpdate = false) {
                ChatThemeView(
                    state = aChatThemeState(
                        current = ChatArtwork.GlassDrops.theme,
                        page = ChatThemePage.BubblePicker(selection = ChatBubbleColor.Purple, selectionDark = ChatBubbleColor.Pink),
                        eventSink = EventsRecorder(expectEvents = false),
                    ),
                    onBackClick = EnsureNeverCalled(),
                )
            }
        }
        val context: Context = activity!!
        onNodeWithContentDescription(context.getString(ChatBubbleColor.Purple.nameRes)).assertIsSelected()
        onNodeWithContentDescription(context.getString(ChatBubbleColor.Pink.nameRes)).assertIsNotSelected()
        isDark = true
        onNodeWithContentDescription(context.getString(ChatBubbleColor.Pink.nameRes)).assertIsSelected()
        onNodeWithContentDescription(context.getString(ChatBubbleColor.Purple.nameRes)).assertIsNotSelected()
        onNodeWithContentDescription(context.getString(ChatBubbleColor.Pink.nameRes)).performClick()
        clickOn(R.string.gua_chat_theme_action_set)
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
    fun `the bubble picker cannot set what it started from in a chat that follows the default`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(
            aChatThemeState(
                defaultTheme = oceanTeal,
                page = ChatThemePage.BubblePicker(selection = ChatBubbleColor.Teal),
                eventSink = EventsRecorder(expectEvents = false),
            )
        )
        clickOn(R.string.gua_chat_theme_action_set)
    }

    @Test
    fun `the bubble picker cannot remove a theme the chat does not have`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(
            aChatThemeState(
                defaultTheme = ChatTheme(aChatBackgroundPhoto(), ChatBubbleColor.Blue),
                page = ChatThemePage.BubblePicker(selection = null),
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
        onNodeWithText(activity!!.getString(R.string.gua_chat_theme_no_wallpaper)).assertIsNotSelected()
        clickOn(R.string.gua_chat_theme_no_wallpaper)
        onNodeWithContentDescription(activity!!.getString(ChatBackgroundPreset.Ocean.nameRes)).assertIsSelected()
        scrollToAndClickTile(ChatBackgroundPreset.Mint.nameRes)
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
    fun `the wallpaper picker offers the artworks by name and marks the current one`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(current = ChatArtwork.Azulejo.theme, page = ChatThemePage.WallpaperPicker, eventSink = eventsRecorder))
        onNodeWithContentDescription(activity!!.getString(ChatArtwork.Azulejo.nameRes)).assertIsSelected()
        onNodeWithContentDescription(activity!!.getString(ChatArtwork.Silk.nameRes), substring = true).assertIsNotSelected()
        scrollToAndClickTile(ChatArtwork.PaperCut.nameRes)
        eventsRecorder.assertSingle(ChatThemeEvent.SelectWallpaper(ChatBackground.Art(ChatArtwork.PaperCut)))
    }

    @Test
    fun `a live wallpaper in the picker is read by its name, then as live`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(current = ChatArtwork.GlassDrops.theme, page = ChatThemePage.WallpaperPicker))
        val context: Context = activity!!
        val glassDrops = context.getString(ChatArtwork.GlassDrops.nameRes)
        onNodeWithContentDescription("$glassDrops, ${context.getString(R.string.gua_chat_theme_live_badge)}").assertIsSelected()
        onNodeWithText(glassDrops).assertDoesNotExist()
    }

    @Test
    fun `no wallpaper reads as selected while the theme has no wallpaper`() = runAndroidComposeUiTest<ComponentActivity> {
        setChatThemeView(aChatThemeState(current = ChatTheme(bubble = ChatBubbleColor.Blue), page = ChatThemePage.WallpaperPicker))
        onNodeWithText(activity!!.getString(R.string.gua_chat_theme_no_wallpaper)).assertIsSelected()
    }

    @Test
    fun `the photo preview saves the photo, and back cancels it`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(photoDraft = aChatBackgroundPhotoDraft(), eventSink = eventsRecorder))
        clickOn(R.string.gua_chat_background_preview_dark)
        clickOn(R.string.gua_chat_theme_action_set)
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
        clickOn(R.string.gua_chat_theme_action_set)
    }

    @Test
    fun `a failed save shows the existing error and dismissing it clears it`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        setChatThemeView(aChatThemeState(action = AsyncAction.Failure(IllegalStateException("403")), eventSink = eventsRecorder))
        onNodeWithText(activity!!.getString(R.string.gua_chat_background_error_generic)).assertExists()
        clickOn(CommonStrings.action_ok)
        eventsRecorder.assertSingle(ChatThemeEvent.ClearError)
    }

    @Test
    fun `the preview of a live artwork previews a send without saving, and Set still saves`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<ChatThemeEvent>()
        val glassDrops = ChatArtwork.GlassDrops.theme
        setChatThemeView(aChatThemeState(preview = ChatThemePreview(glassDrops, glassDrops), eventSink = eventsRecorder))
        val previewSend = activity!!.getString(R.string.gua_chat_theme_preview_send_a11y)
        val wallpaper = onNode(SemanticsMatcher.keyIsDefined(LiveWallpaperCuesKey))
        wallpaper.assert(SemanticsMatcher.expectValue(LiveWallpaperCuesKey, LiveWallpaperCues(sendCount = 0)))
        onNodeWithText(activity!!.getString(R.string.gua_chat_background_sample_outgoing))
            .assert(SemanticsMatcher("offers $previewSend") { it.config.getOrNull(SemanticsActions.OnClick)?.label == previewSend })
            .performSemanticsAction(SemanticsActions.OnClick)
        wallpaper.assert(SemanticsMatcher.expectValue(LiveWallpaperCuesKey, LiveWallpaperCues(sendCount = 1)))
        onNodeWithText(activity!!.getString(R.string.gua_chat_background_sample_incoming)).performClick()
        wallpaper.assert(SemanticsMatcher.expectValue(LiveWallpaperCuesKey, LiveWallpaperCues(sendCount = 2)))
        clickOn(R.string.gua_chat_theme_action_set_theme)
        eventsRecorder.assertSingle(ChatThemeEvent.Save)
    }

    @Test
    fun `the preview of a still artwork offers no send preview`() = runAndroidComposeUiTest<ComponentActivity> {
        val palmCoast = ChatArtwork.PalmCoast.theme
        setChatThemeView(aChatThemeState(preview = ChatThemePreview(palmCoast, palmCoast), eventSink = EventsRecorder(expectEvents = false)))
        onNodeWithText(activity!!.getString(R.string.gua_chat_background_sample_outgoing)).assert(hasClickAction().not())
        onNodeWithText(activity!!.getString(R.string.gua_chat_background_sample_incoming)).performClick()
        onNode(SemanticsMatcher.keyIsDefined(LiveWallpaperCuesKey))
            .assert(SemanticsMatcher.expectValue(LiveWallpaperCuesKey, LiveWallpaperCues(sendCount = 0)))
    }

    @Test
    fun `the preview of a live artwork offers no send preview while Battery Saver shows its still`() = runAndroidComposeUiTest<ComponentActivity> {
        val powerManager = activity!!.getSystemService(PowerManager::class.java)
        shadowOf(powerManager).setIsPowerSaveMode(true)
        try {
            val silk = ChatArtwork.Silk.theme
            setChatThemeView(aChatThemeState(preview = ChatThemePreview(silk, silk), eventSink = EventsRecorder(expectEvents = false)))
            onNodeWithText(activity!!.getString(R.string.gua_chat_background_sample_outgoing)).assert(hasClickAction().not())
        } finally {
            shadowOf(powerManager).setIsPowerSaveMode(false)
        }
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.scrollToAndClickTile(@StringRes res: Int) {
    onNode(hasContentDescription(activity!!.getString(res)) and hasClickAction()).performScrollTo().performClick()
}

private fun AndroidComposeUiTest<ComponentActivity>.setChatThemeView(
    state: ChatThemeState,
    onBackClick: () -> Unit = EnsureNeverCalled(),
    isDark: Boolean = false,
) {
    setContent {
        ElementTheme(theme = if (isDark) Theme.Dark else Theme.Light, applySystemBarsUpdate = false) {
            ChatThemeView(
                state = state,
                onBackClick = onBackClick,
            )
        }
    }
}
