/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.timeline.components.event

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.actions.MessageAction
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.wysiwyg.link.Link
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class TimelineItemMessageActionsViewTest : RobolectricTest() {
    private val first = MessageAction(label = "View statement", url = "https://bills.example.org/statement")
    private val second = MessageAction(label = "Download the PDF", url = "https://bills.example.org/statement.pdf")

    @Test
    fun `clicking an action opens its url through the link flow`() = runAndroidComposeUiTest<ComponentActivity> {
        val onLinkClick = EventsRecorder<Link>()
        setContent {
            TimelineItemMessageActionsView(actions = persistentListOf(first, second), onLinkClick = onLinkClick)
        }
        onNodeWithText(second.label).performClick()
        onLinkClick.assertSingle(Link(url = second.url, text = second.label))
    }

    @Test
    fun `each action is a button with a 48 dp touch target`() = runAndroidComposeUiTest<ComponentActivity> {
        setContent {
            TimelineItemMessageActionsView(actions = persistentListOf(first, second), onLinkClick = EventsRecorder(expectEvents = false))
        }
        listOf(first, second).forEach { action ->
            val button = onNodeWithText(action.label)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .fetchSemanticsNode()
            assertThat(with(density) { button.touchBoundsInRoot.height.toDp() }).isAtLeast(48.dp)
        }
    }
}
