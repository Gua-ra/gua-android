/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.api.gif.GifImageLoader
import io.element.android.features.expressions.api.panel.ExpressionPanelRenderer
import io.element.android.features.expressions.api.panel.ExpressionPanelState
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.emoji.api.picker.EmojiPickerRenderer

@ContributesBinding(SessionScope::class)
class DefaultExpressionPanelRenderer(
    private val emojiPickerRenderer: EmojiPickerRenderer,
    private val gifImageLoader: GifImageLoader,
) : ExpressionPanelRenderer {
    @Composable
    override fun ToggleButton(state: ExpressionPanelState, onClick: () -> Unit) {
        ExpressionToggleButton(isPanelOpen = state.isOpen, onClick = onClick)
    }

    @Composable
    override fun Panel(state: ExpressionPanelState, keyboardHeight: Dp, modifier: Modifier) {
        if (state is DefaultExpressionPanelState && state.isOpen) {
            ExpressionPanelView(
                state = state,
                heights = PanelHeights.of(keyboardHeight, LocalConfiguration.current.screenHeightDp.dp),
                emojiPickerRenderer = emojiPickerRenderer,
                gifImageLoader = gifImageLoader.imageLoader,
                modifier = modifier,
            )
        }
    }

    @Composable
    override fun Overlays(state: ExpressionPanelState) {
        if (state is DefaultExpressionPanelState && state.isEnabled) {
            ExpressionOverlaysView(state = state, gifImageLoader = gifImageLoader.imageLoader)
        }
    }
}
