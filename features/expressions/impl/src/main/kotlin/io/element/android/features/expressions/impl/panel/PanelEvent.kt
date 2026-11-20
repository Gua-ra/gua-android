/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.impl.editor.EditorFrame

sealed interface PanelEvent {
    data class SelectTab(val tab: ExpressionTab) : PanelEvent

    data class UpdateQuery(val query: String) : PanelEvent

    data class InsertEmoji(val emoji: String) : PanelEvent

    data class SendSticker(val image: StickerImage) : PanelEvent

    data class ShowStickerActions(val tile: StickerTile, val section: StickerSectionKind) : PanelEvent

    data class ToggleStickerFavourite(val image: StickerImage) : PanelEvent

    data class RemoveRecentSticker(val url: String) : PanelEvent

    data class RequestDeleteSticker(val shortcode: String) : PanelEvent

    data object ConfirmDeleteSticker : PanelEvent

    data class SaveSticker(val image: StickerImage) : PanelEvent

    data class BrowseGifs(val browse: GifBrowse) : PanelEvent

    data object LoadMoreGifs : PanelEvent

    data object RetryGifs : PanelEvent

    data class OpenGif(val item: GifItem) : PanelEvent

    data class ToggleGifFavourite(val item: GifItem) : PanelEvent

    data class SendGif(val item: GifItem, val caption: String) : PanelEvent

    data object CreateSticker : PanelEvent

    data class SetCutOut(val isOn: Boolean) : PanelEvent

    data class UpdateStickerText(val text: String) : PanelEvent

    data class SaveCreatedSticker(val frame: EditorFrame, val send: Boolean) : PanelEvent

    data object DismissOverlay : PanelEvent
}
