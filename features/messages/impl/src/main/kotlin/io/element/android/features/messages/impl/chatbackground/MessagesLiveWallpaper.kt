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
import io.element.android.features.messages.impl.link.ConfirmingLinkClick
import io.element.android.features.messages.impl.messagemenu.MessageMenuState
import io.element.android.features.messages.impl.timeline.components.customreaction.CustomReactionState
import io.element.android.features.messages.impl.timeline.sendfailure.SendFailureDialogState
import io.element.android.libraries.architecture.overlay.Overlay
import io.element.android.libraries.matrix.ui.chatbackground.LiveWallpaperCues
import io.element.android.libraries.matrix.ui.chatbackground.LocalLiveWallpaperHidden

internal fun MessagesState.liveWallpaperCues(isChatHidden: Boolean = false) = LiveWallpaperCues(
    isCovered = isChatHidden || isTimelineCovered(),
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
        timelineState.resolveVerifiedUserSendFailureState.verifiedUserSendFailure !is VerifiedUserSendFailure.None ||
        expressionPanelState.isShowingOverlay ||
        isDialogShown()
}

/** Misses the end-poll confirmation and the member moderation sheet, whose state is not in [MessagesState]. */
private fun MessagesState.isDialogShown(): Boolean {
    return redactEventAction is MessagesState.ConfirmingRedaction ||
        showReinvitePrompt ||
        linkState.linkClick is ConfirmingLinkClick ||
        timelineState.sendFailureDialogState is SendFailureDialogState.Show ||
        timelineState.messageShieldDialogData != null ||
        voiceMessageComposerState.showPermissionRationaleDialog ||
        voiceMessageComposerState.showSendFailureDialog
}

/** Pauses live wallpapers in [content] while [overlay] shows a screen, such as the media viewer, that leaves the chat under it resumed. */
@Composable
internal fun <T : Any> PauseLiveWallpapersUnder(overlay: Overlay<T>, content: @Composable () -> Unit) {
    val elements by overlay.elements.collectAsState()
    CompositionLocalProvider(LocalLiveWallpaperHidden provides elements.isOverlayShown(), content = content)
}

/** A screen on its way out of the overlay no longer hides the chat. */
internal fun <T> NavElements<T, BackStack.State>.isOverlayShown(): Boolean = any { it.targetState == BackStack.State.ACTIVE }
