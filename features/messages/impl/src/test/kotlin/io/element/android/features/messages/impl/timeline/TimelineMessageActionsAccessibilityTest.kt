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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.actions.MessageAction
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.features.messages.impl.timeline.protection.aTimelineProtectionState
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.setSafeContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class TimelineMessageActionsAccessibilityTest : RobolectricTest() {
    private val event = aTimelineItemEvent(
        content = TimelineItemTextContent(
            body = "Total: 10",
            htmlDocument = null,
            formattedBody = "Total: 10",
            isEdited = false,
            actions = persistentListOf(MessageAction(label = LABEL, url = "https://bills.example.org/statement")),
        ),
    )

    @Test
    fun `the action buttons are hidden from screen readers only while the row toggles the selection`() = runAndroidComposeUiTest<ComponentActivity> {
        var selectionState: SelectionState by mutableStateOf(SelectionState.Disabled)
        setTimeline { selectionState }
        val hiddenAncestor = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        onNodeWithText(LABEL, useUnmergedTree = true).assert(!hiddenAncestor)

        selectionState = SelectionState.Active(SelectionAction.Forward, persistentSetOf())
        onNodeWithText(LABEL, useUnmergedTree = true).assert(hiddenAncestor)
    }

    @Test
    fun `a screen reader reads the label once, on the button, and the icon adds no description`() = runAndroidComposeUiTest<ComponentActivity> {
        setTimeline()
        // The bubble merges its children's semantics, but not a clickable child's, so the label must stay on the button alone.
        onAllNodesWithText(LABEL).assertCountEquals(1)
        onNodeWithText(LABEL)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(LABEL))))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `the timestamp stays below the touch target of the button`() = runAndroidComposeUiTest<ComponentActivity> {
        setTimeline()
        val button = onNodeWithText(LABEL).fetchSemanticsNode()
        val timestamp = onNodeWithText(event.sentTime, useUnmergedTree = true).fetchSemanticsNode()
        assertThat(timestamp.boundsInRoot.top).isAtLeast(button.touchBoundsInRoot.bottom)
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setTimeline(selectionState: () -> SelectionState = { SelectionState.Disabled }) {
        setSafeContent(clearAndroidUiDispatcher = true) {
            TimelineView(
                state = aTimelineState(timelineItems = persistentListOf(event), selectionState = selectionState()),
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
    }

    private companion object {
        const val LABEL = "View statement"
    }
}
