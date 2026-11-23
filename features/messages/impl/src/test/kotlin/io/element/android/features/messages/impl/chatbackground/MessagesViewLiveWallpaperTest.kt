/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.chatbackground

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.MessagesState
import io.element.android.features.messages.impl.MessagesView
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.aMessagesState
import io.element.android.features.messages.impl.aReadReceiptBottomSheetState
import io.element.android.features.messages.impl.aUserEventPermissions
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineState
import io.element.android.libraries.architecture.overlay.Overlay
import io.element.android.libraries.architecture.overlay.operation.hide
import io.element.android.libraries.architecture.overlay.operation.show
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.matrix.ui.chatbackground.LiveWallpaperCues
import io.element.android.libraries.matrix.ui.chatbackground.LiveWallpaperCuesKey
import io.element.android.libraries.matrix.ui.chatbackground.LiveWallpaperVisibleAreaKey
import io.element.android.libraries.matrix.ui.chatbackground.LocalLiveWallpaperHidden
import io.element.android.libraries.testtags.TestTags
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.setSafeContent
import org.junit.Test

class MessagesViewLiveWallpaperTest : RobolectricTest() {
    @Test
    fun `the timeline's wallpaper gets the user's sends`() = runAndroidComposeUiTest<ComponentActivity> {
        setMessagesView(aMessagesState(chatTheme = ChatArtwork.GlassDrops.theme, timelineState = aTimelineState(ownNewEventCount = 2)))
        onNode(SemanticsMatcher.keyIsDefined(LiveWallpaperCuesKey))
            .assert(SemanticsMatcher.expectValue(LiveWallpaperCuesKey, LiveWallpaperCues(isCovered = false, sendCount = 2)))
    }

    @Test
    fun `the timeline's wallpaper is covered by a sheet over it`() = runAndroidComposeUiTest<ComponentActivity> {
        setMessagesView(
            aMessagesState(
                chatTheme = ChatArtwork.Silk.theme,
                readReceiptBottomSheetState = aReadReceiptBottomSheetState(selectedEvent = aTimelineItemEvent()),
            )
        )
        onNode(SemanticsMatcher.keyIsDefined(LiveWallpaperCuesKey))
            .assert(SemanticsMatcher.expectValue(LiveWallpaperCuesKey, LiveWallpaperCues(isCovered = true, sendCount = 0)))
    }

    @Test
    fun `the timeline's wallpaper is covered while the top bar and the composer leave no room for the chat`() = runAndroidComposeUiTest<ComponentActivity> {
        setMessagesView(aMessagesState(chatTheme = ChatArtwork.GlassDrops.theme), modifier = Modifier.height(100.dp))
        onNode(SemanticsMatcher.keyIsDefined(LiveWallpaperCuesKey))
            .assert(SemanticsMatcher.expectValue(LiveWallpaperCuesKey, LiveWallpaperCues(isCovered = true, sendCount = 0)))
    }

    @Test
    fun `the timeline's wallpaper starts its send pulse in the chat between the top bar and the composer`() = runAndroidComposeUiTest<ComponentActivity> {
        setMessagesView(aMessagesState(chatTheme = ChatArtwork.GlassDrops.theme))
        val wallpaper = onNode(SemanticsMatcher.keyIsDefined(LiveWallpaperVisibleAreaKey)).fetchSemanticsNode()
        val area = wallpaper.config[LiveWallpaperVisibleAreaKey]
        val composer = onNodeWithTag(TestTags.textEditor.value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        runOnIdle {
            assertThat(area.top).isWithin(0.5f).of(wallpaper.boundsInRoot.top)
            assertThat(area.bottom).isLessThan(wallpaper.boundsInRoot.bottom)
            assertThat(area.bottom).isAtMost(composer.top)
            assertThat(composer.top - area.bottom!!).isLessThan(with(density) { 24.dp.toPx() })
        }
    }

    @Test
    fun `in a room the user cannot post to, the wallpaper's chat ends above the banner in the composer's place`() =
        runAndroidComposeUiTest<ComponentActivity> {
            setMessagesView(aMessagesState(chatTheme = ChatArtwork.Silk.theme, userEventPermissions = aUserEventPermissions(canSendMessage = false)))
            val wallpaper = onNode(SemanticsMatcher.keyIsDefined(LiveWallpaperVisibleAreaKey)).fetchSemanticsNode()
            val area = wallpaper.config[LiveWallpaperVisibleAreaKey]
            val banner = onNodeWithText(activity!!.getString(R.string.screen_room_timeline_no_permission_to_post)).fetchSemanticsNode().boundsInRoot
            runOnIdle {
                assertThat(area.top).isWithin(0.5f).of(wallpaper.boundsInRoot.top)
                assertThat(area.bottom).isLessThan(wallpaper.boundsInRoot.bottom)
                assertThat(area.bottom).isAtMost(banner.top)
                assertThat(banner.top - area.bottom!!).isLessThan(with(density) { 24.dp.toPx() })
            }
        }

    @Test
    fun `live wallpapers are hidden while the overlay shows a screen over the chat`() = runAndroidComposeUiTest<ComponentActivity> {
        val overlay = Overlay<String>(savedStateMap = null)
        var isHidden: Boolean? = null
        setContent {
            PauseLiveWallpapersUnder(overlay) { isHidden = LocalLiveWallpaperHidden.current }
        }
        runOnIdle { assertThat(isHidden).isFalse() }
        runOnUiThread { overlay.show("media viewer") }
        runOnIdle { assertThat(isHidden).isTrue() }
        runOnUiThread { overlay.hide() }
        runOnIdle { assertThat(isHidden).isFalse() }
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.setMessagesView(state: MessagesState, modifier: Modifier = Modifier) {
    setSafeContent {
        // The rich text editor cannot run here.
        CompositionLocalProvider(LocalInspectionMode provides true) {
            MessagesView(
                state = state,
                onBackClick = {},
                onRoomDetailsClick = {},
                onEventContentClick = { _, _ -> false },
                onGalleryEventItemClick = { _, _, _ -> false },
                onUserDataClick = {},
                onLinkClick = { _, _ -> },
                onSendLocationClick = {},
                onCreatePollClick = {},
                onJoinCallClick = {},
                onViewAllPinnedMessagesClick = {},
                onThreadsListClick = {},
                knockRequestsBannerView = {},
                modifier = modifier,
                customReactionBottomSheet = {},
            )
        }
    }
}
