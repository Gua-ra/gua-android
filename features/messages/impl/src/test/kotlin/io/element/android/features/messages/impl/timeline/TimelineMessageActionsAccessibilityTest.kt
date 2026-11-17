/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.timeline

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.messages.impl.timeline.actions.MessageAction
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.features.messages.impl.timeline.protection.aTimelineProtectionState
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.setSafeContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class TimelineMessageActionsAccessibilityTest : RobolectricTest() {
    @Test
    fun `the action buttons are hidden from screen readers only while the row toggles the selection`() = runAndroidComposeUiTest<ComponentActivity> {
        val content = TimelineItemTextContent(
            body = "Total: 10",
            htmlDocument = null,
            formattedBody = "Total: 10",
            isEdited = false,
            actions = persistentListOf(MessageAction(label = LABEL, url = "https://bills.example.org/statement")),
        )
        val timelineItems = persistentListOf(aTimelineItemEvent(content = content))
        var selectionState: SelectionState by mutableStateOf(SelectionState.Disabled)
        setSafeContent(clearAndroidUiDispatcher = true) {
            TimelineView(
                state = aTimelineState(timelineItems = timelineItems, selectionState = selectionState),
                timelineProtectionState = aTimelineProtectionState(),
                onUserDataClick = {},
                onLinkClick = {},
                onContentClick = {},
                onGalleryItemClick = { _, _ -> },
                onMessageLongClick = {},
                onSwipeToReply = {},
                onReactionClick = { _, _ -> },
                onReactionLongClick = { _, _ -> },
                onMoreReactionsClick = {},
                onReadReceiptClick = {},
                onJoinCallClick = {},
            )
        }
        val hiddenAncestor = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        onNodeWithText(LABEL, useUnmergedTree = true).assert(!hiddenAncestor)

        selectionState = SelectionState.Active(SelectionAction.Forward, persistentSetOf())
        onNodeWithText(LABEL, useUnmergedTree = true).assert(hiddenAncestor)
    }

    private companion object {
        const val LABEL = "View statement"
    }
}
