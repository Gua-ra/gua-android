/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Immutable
interface ExpressionPanelRenderer {
    @Composable
    fun ToggleButton(state: ExpressionPanelState, onClick: () -> Unit)

    @Composable
    fun Panel(state: ExpressionPanelState, keyboardHeight: Dp, modifier: Modifier)

    @Composable
    fun Overlays(state: ExpressionPanelState)
}

object NoOpExpressionPanelRenderer : ExpressionPanelRenderer {
    @Composable
    override fun ToggleButton(state: ExpressionPanelState, onClick: () -> Unit) = Unit

    @Composable
    override fun Panel(state: ExpressionPanelState, keyboardHeight: Dp, modifier: Modifier) = Unit

    @Composable
    override fun Overlays(state: ExpressionPanelState) = Unit
}
