/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatbackground

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.api.timeline.voicemessages.composer.aVoiceMessageComposerState
import io.element.android.features.messages.impl.MessagesState
import io.element.android.features.messages.impl.aCustomReactionState
import io.element.android.features.messages.impl.aMessagesState
import io.element.android.features.messages.impl.aReactionSummaryState
import io.element.android.features.messages.impl.aReadReceiptBottomSheetState
import io.element.android.features.messages.impl.actionlist.ActionListState
import io.element.android.features.messages.impl.actionlist.anActionListState
import io.element.android.features.messages.impl.crypto.sendfailure.resolve.aResolveVerifiedUserSendFailureState
import io.element.android.features.messages.impl.crypto.sendfailure.resolve.anUnsignedDeviceSendFailure
import io.element.android.features.messages.impl.link.ConfirmingLinkClick
import io.element.android.features.messages.impl.link.aLink
import io.element.android.features.messages.impl.link.aLinkState
import io.element.android.features.messages.impl.messagecomposer.aMessageComposerState
import io.element.android.features.messages.impl.messagemenu.MessageMenuGesture
import io.element.android.features.messages.impl.messagemenu.MessageMenuSections
import io.element.android.features.messages.impl.messagemenu.MessageMenuState
import io.element.android.features.messages.impl.messagemenu.MessageQuickReactions
import io.element.android.features.messages.impl.messagemenu.aMessageMenuState
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineState
import io.element.android.features.messages.impl.timeline.components.aCriticalShield
import io.element.android.features.messages.impl.timeline.components.customreaction.CustomReactionState
import io.element.android.features.messages.impl.timeline.components.reactionsummary.ReactionSummaryState
import io.element.android.features.messages.impl.timeline.sendfailure.SendFailureDialogState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.ui.chatbackground.LiveWallpaperCues
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class MessagesLiveWallpaperTest {
    private val event = aTimelineItemEvent()

    @Test
    fun `the wallpaper gets the timeline's count of the user's sends`() {
        val state = aMessagesState(timelineState = aTimelineState(ownNewEventCount = 3))
        assertThat(state.liveWallpaperCues()).isEqualTo(LiveWallpaperCues(isCovered = false, sendCount = 3))
    }

    @Test
    fun `nothing over the timeline leaves it uncovered`() {
        assertThat(aMessagesState().isTimelineCovered()).isFalse()
    }

    @Test
    fun `a chat squeezed to nothing covers the wallpaper`() {
        assertThat(aMessagesState().liveWallpaperCues(isChatHidden = true)).isEqualTo(LiveWallpaperCues(isCovered = true, sendCount = 0))
    }

    @Test
    fun `the message menu and its text selection cover the timeline`() {
        val shown = MessageMenuState.Target.Shown(
            event = event,
            gesture = MessageMenuGesture.LongPress,
            reactions = MessageQuickReactions.defaults,
            highlightedReactions = persistentListOf(),
            sections = MessageMenuSections.Empty,
            reactionRows = persistentListOf(),
        )
        assertThat(aMessagesState(messageMenuState = aMessageMenuState(target = shown)).isTimelineCovered()).isTrue()
        assertThat(aMessagesState(messageMenuState = aMessageMenuState(selectableText = "Hello")).isTimelineCovered()).isTrue()
    }

    @Test
    fun `every bottom sheet over the timeline covers it`() {
        listOf(
            aMessagesState(actionListState = anActionListState(target = ActionListState.Target.Loading(event))),
            aMessagesState(customReactionState = aCustomReactionState(target = CustomReactionState.Target.Loading(event))),
            aMessagesState(
                reactionSummaryState = aReactionSummaryState(
                    target = ReactionSummaryState.Summary(reactions = persistentListOf(), selectedKey = "👍", selectedEventId = EventId("\$event")),
                ),
            ),
            aMessagesState(readReceiptBottomSheetState = aReadReceiptBottomSheetState(selectedEvent = event)),
            aMessagesState(composerState = aMessageComposerState(showAttachmentSourcePicker = true)),
            aMessagesState(
                timelineState = aTimelineState(
                    resolveVerifiedUserSendFailureState = aResolveVerifiedUserSendFailureState(verifiedUserSendFailure = anUnsignedDeviceSendFailure()),
                ),
            ),
        ).forEach { state ->
            assertThat(state.isTimelineCovered()).isTrue()
        }
    }

    @Test
    fun `every dialog the state opens over the timeline covers it`() {
        listOf(
            aMessagesState(redactEventAction = MessagesState.ConfirmingRedaction(EventId("\$event"))),
            aMessagesState(showReinvitePrompt = true),
            aMessagesState(linkState = aLinkState(linkClick = ConfirmingLinkClick(aLink))),
            aMessagesState(
                timelineState = aTimelineState(
                    sendFailureDialogState = SendFailureDialogState.Show(
                        event = event,
                        sendFailureType = SendFailureDialogState.SendFailureType.Unknown,
                    ),
                ),
            ),
            aMessagesState(timelineState = aTimelineState(messageShield = aCriticalShield())),
            aMessagesState(voiceMessageComposerState = aVoiceMessageComposerState(showPermissionRationaleDialog = true)),
            aMessagesState(voiceMessageComposerState = aVoiceMessageComposerState(showSendFailureDialog = true)),
        ).forEach { state ->
            assertThat(state.isTimelineCovered()).isTrue()
        }
    }

    @Test
    fun `a removal that is not waiting for confirmation, or a link that needs none, leaves the timeline uncovered`() {
        assertThat(aMessagesState(redactEventAction = AsyncAction.Loading).isTimelineCovered()).isFalse()
        assertThat(aMessagesState(linkState = aLinkState(linkClick = AsyncAction.Success(aLink))).isTimelineCovered()).isFalse()
    }
}
