/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.features.expressions.api.panel.ExpressionPanelRenderer
import io.element.android.features.expressions.api.panel.ExpressionPanelState
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.setSafeContent
import org.junit.Test
import org.robolectric.annotation.Config

class MessagesViewExpressionPanelTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "h1024dp")
    fun `an open expression panel gets its own height under the composer`() = runAndroidComposeUiTest<ComponentActivity> {
        setMessagesView(aMessagesState().copy(expressionPanelState = OpenPanelState))

        onNodeWithTag(PANEL_TAG).assertHeightIsEqualTo(PANEL_HEIGHT)
    }
}

private const val PANEL_TAG = "expression_panel"
private val PANEL_HEIGHT = 300.dp

private object OpenPanelState : ExpressionPanelState by ExpressionPanelState.Disabled {
    override val isEnabled = true
    override val isOpen = true
}

private object FixedHeightPanelRenderer : ExpressionPanelRenderer {
    @Composable
    override fun ToggleButton(state: ExpressionPanelState, onClick: () -> Unit) = Unit

    @Composable
    override fun Panel(state: ExpressionPanelState, keyboardHeight: Dp, modifier: Modifier) {
        if (state.isOpen) Box(modifier.height(PANEL_HEIGHT).testTag(PANEL_TAG))
    }

    @Composable
    override fun Overlays(state: ExpressionPanelState) = Unit
}

private fun AndroidComposeUiTest<ComponentActivity>.setMessagesView(state: MessagesState) {
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
                expressionPanelRenderer = FixedHeightPanelRenderer,
                customReactionBottomSheet = {},
            )
        }
    }
}
