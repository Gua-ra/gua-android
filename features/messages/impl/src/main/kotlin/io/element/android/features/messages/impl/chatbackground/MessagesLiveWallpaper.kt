/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatbackground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bumble.appyx.core.navigation.NavElements
import com.bumble.appyx.navmodel.backstack.BackStack
import io.element.android.features.messages.impl.MessagesState
import io.element.android.features.messages.impl.actionlist.ActionListState
import io.element.android.features.messages.impl.crypto.sendfailure.VerifiedUserSendFailure
import io.element.android.features.messages.impl.messagemenu.MessageMenuState
import io.element.android.features.messages.impl.timeline.components.customreaction.CustomReactionState
import io.element.android.libraries.architecture.overlay.Overlay
import io.element.android.libraries.matrix.ui.chatbackground.LiveWallpaperCues
import io.element.android.libraries.matrix.ui.chatbackground.LocalLiveWallpaperHidden

/** What the timeline's live wallpaper needs: whether a sheet or the message menu covers it, and the user's sends from this device. */
internal fun MessagesState.liveWallpaperCues() = LiveWallpaperCues(
    isCovered = isTimelineCovered(),
    sendCount = timelineState.ownNewEventCount,
)

internal fun MessagesState.isTimelineCovered(): Boolean {
    return messageMenuState.target is MessageMenuState.Target.Shown ||
        messageMenuState.selectableText != null ||
        actionListState.target != ActionListState.Target.None ||
        customReactionState.target != CustomReactionState.Target.None ||
        reactionSummaryState.target != null ||
        readReceiptBottomSheetState.selectedEvent != null ||
        composerState.showAttachmentSourcePicker ||
        timelineState.resolveVerifiedUserSendFailureState.verifiedUserSendFailure !is VerifiedUserSendFailure.None
}

/**
 * The screens of [content] with their live wallpapers paused while [overlay] shows a screen over them. The media viewer, the gallery
 * and the avatar preview open there, and leave the chat under them resumed.
 */
@Composable
internal fun <T : Any> PauseLiveWallpapersUnder(overlay: Overlay<T>, content: @Composable () -> Unit) {
    val elements by overlay.elements.collectAsState()
    CompositionLocalProvider(LocalLiveWallpaperHidden provides elements.isOverlayShown(), content = content)
}

/** Whether an overlay screen is showing or on its way in; one on its way out no longer hides the screen under it. */
internal fun <T> NavElements<T, BackStack.State>.isOverlayShown(): Boolean = any { it.targetState == BackStack.State.ACTIVE }
