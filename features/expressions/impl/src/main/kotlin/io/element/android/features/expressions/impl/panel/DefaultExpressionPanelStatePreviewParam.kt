/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.expressions.api.gif.GifFile
import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifRendition
import io.element.android.features.expressions.api.gif.GifSize
import io.element.android.features.expressions.api.sticker.StickerImage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

open class DefaultExpressionPanelStatePreviewParam : PreviewParameterProvider<DefaultExpressionPanelState> {
    override val values: Sequence<DefaultExpressionPanelState>
        get() = sequenceOf(
            aDefaultExpressionPanelState(),
            aDefaultExpressionPanelState(stickerSections = persistentListOf(StickerSection(StickerSectionKind.Recent, persistentListOf()))),
            aDefaultExpressionPanelState(tab = ExpressionTab.Gif),
            aDefaultExpressionPanelState(tab = ExpressionTab.Gif, gifContent = GifContent.Error),
            aDefaultExpressionPanelState(tab = ExpressionTab.Gif, gifContent = GifContent.Unavailable),
            aDefaultExpressionPanelState(tab = ExpressionTab.Emoji),
        )
}

internal fun aStickerImage(name: String) = StickerImage(url = "mxc://gua.global/$name", body = name, info = null)

internal fun aGifItem(id: String, width: Int = 200, height: Int = 150) = GifItem(
    id = id,
    title = id,
    renditions = mapOf(
        GifSize.Sm to GifRendition(animated = GifFile("https://static.klipy.com/$id-sm.webp", width, height, 40_000), still = null),
        GifSize.Hd to GifRendition(
            animated = GifFile("https://static.klipy.com/$id-hd.webp", width * 2, height * 2, 400_000),
            still = GifFile("https://static.klipy.com/$id-hd.jpg", width * 2, height * 2, 20_000),
        ),
    ),
)

internal fun aDefaultExpressionPanelState(
    tab: ExpressionTab = ExpressionTab.Stickers,
    stickerSections: List<StickerSection> = listOf(
        StickerSection(StickerSectionKind.Recent, listOf("cat", "dog", "wave").map { StickerTile(aStickerImage(it), null) }.toImmutableList()),
        StickerSection(StickerSectionKind.Favourites, listOf("love").map { StickerTile(aStickerImage(it), null) }.toImmutableList()),
        StickerSection(StickerSectionKind.Mine, listOf("me", "you", "us", "them", "hi").map { StickerTile(aStickerImage(it), "s$it") }.toImmutableList()),
        StickerSection(
            StickerSectionKind.Pack("m.room.image_pack", "", "Family", null),
            listOf("party", "cake").map { StickerTile(aStickerImage(it), null) }.toImmutableList(),
        ),
    ),
    gifContent: GifContent = GifContent.Loaded(
        items = listOf(aGifItem("a"), aGifItem("b", height = 280), aGifItem("c", width = 300), aGifItem("d")).toImmutableList(),
        hasNext = true,
        isLoadingMore = false,
    ),
) = DefaultExpressionPanelState(
    isEnabled = true,
    isOpen = true,
    tab = tab,
    stickerQuery = "",
    gifQuery = "",
    emojiPickerState = null,
    stickerSections = stickerSections.toImmutableList(),
    sendingStickerUrls = persistentSetOf(aStickerImage("dog").url),
    favouriteStickerUrls = persistentSetOf(aStickerImage("love").url),
    savedStickerUrls = persistentSetOf(),
    gifBrowse = GifBrowse.Trending,
    gifContent = gifContent,
    favouriteGifIds = persistentSetOf("b"),
    overlay = null,
    panelSink = {},
    eventSink = {},
)
