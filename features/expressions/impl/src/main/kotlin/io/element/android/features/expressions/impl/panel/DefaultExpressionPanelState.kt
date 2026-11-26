/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import io.element.android.features.expressions.api.panel.ExpressionPanelEvent
import io.element.android.features.expressions.api.panel.ExpressionPanelState
import io.element.android.libraries.emoji.api.picker.EmojiPickerState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class DefaultExpressionPanelState(
    override val isEnabled: Boolean,
    override val isOpen: Boolean,
    val tab: ExpressionTab,
    val stickerQuery: String,
    val gifQuery: String,
    val emojiPickerState: EmojiPickerState?,
    val stickerSections: ImmutableList<StickerSection>,
    val sendingStickerUrls: ImmutableSet<String>,
    val favouriteStickerUrls: ImmutableSet<String>,
    val savedStickerUrls: ImmutableSet<String>,
    val gifBrowse: GifBrowse,
    val gifContent: GifContent,
    val favouriteGifIds: ImmutableSet<String>,
    val overlay: ExpressionOverlay?,
    val panelSink: (PanelEvent) -> Unit,
    override val eventSink: (ExpressionPanelEvent) -> Unit,
) : ExpressionPanelState {
    val query: String get() = if (tab == ExpressionTab.Gif) gifQuery else stickerQuery
}
