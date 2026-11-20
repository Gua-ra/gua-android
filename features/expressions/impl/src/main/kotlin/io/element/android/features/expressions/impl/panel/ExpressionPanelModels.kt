/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.sticker.StickerImage
import kotlinx.collections.immutable.ImmutableList

enum class ExpressionTab {
    Emoji,
    Gif,
    Stickers,
}

@Immutable
sealed interface GifBrowse {
    data object Trending : GifBrowse

    data object Recent : GifBrowse

    data object Favourites : GifBrowse
}

@Immutable
sealed interface GifContent {
    data object Unavailable : GifContent

    data object Loading : GifContent

    data object Error : GifContent

    data class Loaded(
        val items: ImmutableList<GifItem>,
        val hasNext: Boolean,
        val isLoadingMore: Boolean,
    ) : GifContent
}

@Immutable
sealed interface ExpressionOverlay {
    data class StickerActions(val tile: StickerTile, val section: StickerSectionKind) : ExpressionOverlay

    data class ConfirmDelete(val shortcode: String) : ExpressionOverlay

    data class GifPreview(val item: GifItem, val isSending: Boolean) : ExpressionOverlay

    data class Editor(val editor: StickerEditorState) : ExpressionOverlay

    data class TimelineSticker(val image: StickerImage, val packName: String?) : ExpressionOverlay
}

data class StickerEditorState(
    val picture: Bitmap?,
    val cutOut: Bitmap?,
    val isCutOutOn: Boolean,
    val text: String,
    val isSaving: Boolean,
    val showsNote: Boolean,
) {
    val shownPicture: Bitmap? get() = if (isCutOutOn) cutOut ?: picture else picture
}
