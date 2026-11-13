/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.expressions.api.sticker.StickerContent
import io.element.android.features.expressions.impl.R
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import io.element.android.features.expressions.api.R as ApiR

private val TILE_SIZE = 72.dp
private val TILE_CELL_MIN_WIDTH = 84.dp
private val MIN_LABEL_SIZE = 7.sp

@Composable
internal fun StickersTabView(
    state: DefaultExpressionPanelState,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val headerIndices = state.stickerSections.runningFold(0) { index, section -> index + 1 + section.tiles.size + section.extraTiles() }
    Column(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TILE_CELL_MIN_WIDTH),
            state = gridState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            state.stickerSections.forEach { section ->
                item(key = section.key(), span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(section.kind)
                }
                if (section.kind == StickerSectionKind.Recent) {
                    item(key = "create") {
                        CreateStickerTile(onClick = { state.panelSink(PanelEvent.CreateSticker) })
                    }
                }
                itemsIndexed(section.tiles, key = { index, _ -> "${section.key()}:$index" }) { _, tile ->
                    StickerTileView(
                        tile = tile,
                        section = section.kind,
                        isSending = tile.image.url in state.sendingStickerUrls,
                        isFavourite = tile.image.url in state.favouriteStickerUrls,
                        eventSink = state.panelSink,
                    )
                }
            }
            if (state.stickerQuery.isNotBlank() && state.stickerSections.all { it.tiles.isEmpty() }) {
                item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.gua_stickers_none_found),
                        modifier = Modifier.padding(16.dp),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            state.stickerSections.forEachIndexed { index, section ->
                SectionShortcut(section = section, onClick = { coroutineScope.launch { gridState.animateScrollToItem(headerIndices[index]) } })
            }
        }
    }
}

@Composable
private fun SectionHeader(kind: StickerSectionKind) {
    Text(
        text = kind.title(),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics { heading() },
        style = ElementTheme.typography.fontBodySmMedium,
        color = ElementTheme.colors.textSecondary,
    )
}

@Composable
private fun SectionShortcut(section: StickerSection, onClick: () -> Unit) {
    val title = section.kind.title()
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = title }) {
        when (val kind = section.kind) {
            StickerSectionKind.Recent -> Icon(imageVector = CompoundIcons.Time(), contentDescription = null)
            StickerSectionKind.Favourites -> Icon(imageVector = CompoundIcons.Favourite(), contentDescription = null)
            StickerSectionKind.Mine -> section.tiles.firstOrNull()?.let { StickerImageView(it.image, null, Modifier.size(28.dp)) }
                ?: Icon(imageVector = CompoundIcons.Sticker(), contentDescription = null)
            is StickerSectionKind.Pack -> section.tiles.firstOrNull()?.let { StickerImageView(it.image, null, Modifier.size(28.dp)) }
                ?: Icon(imageVector = CompoundIcons.Sticker(), contentDescription = kind.name)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreateStickerTile(onClick: () -> Unit) {
    val label = stringResource(R.string.gua_stickers_create)
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(TILE_SIZE)
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .combinedClickable(onClick = onClick, onClickLabel = label)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = CompoundIcons.Plus(), contentDescription = null, tint = ElementTheme.colors.iconSecondary, modifier = Modifier.size(20.dp))
            val style = ElementTheme.typography.fontBodyXsMedium
            // Shrinks rather than clips when large text would not fit two lines in the tile.
            BasicText(
                text = label,
                modifier = Modifier.weight(1f, fill = false),
                style = style.copy(color = ElementTheme.colors.textSecondary, textAlign = TextAlign.Center),
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = style.fontSize),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTileView(
    tile: StickerTile,
    section: StickerSectionKind,
    isSending: Boolean,
    isFavourite: Boolean,
    eventSink: (PanelEvent) -> Unit,
) {
    val label = stringResource(ApiR.string.gua_a11y_sticker, tile.image.body ?: StickerContent.DEFAULT_BODY)
    val actions = stickerActions(tile, section, isFavourite, eventSink)
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(TILE_SIZE)
            .combinedClickable(
                onClick = { eventSink(PanelEvent.SendSticker(tile.image)) },
                onLongClick = { eventSink(PanelEvent.ShowStickerActions(tile, section)) },
            )
            .semantics {
                contentDescription = label
                customActions = actions.map { (actionLabel, action) ->
                    CustomAccessibilityAction(actionLabel) {
                        action()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        StickerImageView(tile.image, contentDescription = null, modifier = Modifier.fillMaxSize())
        if (isSending) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun stickerActions(
    tile: StickerTile,
    section: StickerSectionKind,
    isFavourite: Boolean,
    eventSink: (PanelEvent) -> Unit,
): ImmutableList<Pair<String, () -> Unit>> = buildList {
    if (isFavourite) {
        add(stringResource(R.string.gua_stickers_remove_favourite) to { eventSink(PanelEvent.ToggleStickerFavourite(tile.image)) })
    } else {
        add(stringResource(R.string.gua_stickers_add_favourite) to { eventSink(PanelEvent.ToggleStickerFavourite(tile.image)) })
    }
    when (section) {
        StickerSectionKind.Recent -> add(stringResource(R.string.gua_stickers_remove_recent) to { eventSink(PanelEvent.RemoveRecentSticker(tile.image.url)) })
        StickerSectionKind.Mine -> tile.shortcode?.let { shortcode ->
            add(stringResource(CommonStrings.action_delete) to { eventSink(PanelEvent.RequestDeleteSticker(shortcode)) })
        }
        is StickerSectionKind.Pack -> add(stringResource(R.string.gua_stickers_save) to { eventSink(PanelEvent.SaveSticker(tile.image)) })
        StickerSectionKind.Favourites -> Unit
    }
}.toImmutableList()

@Composable
internal fun StickerSectionKind.title(): String = when (this) {
    StickerSectionKind.Recent -> stringResource(R.string.gua_stickers_section_recent)
    StickerSectionKind.Favourites -> stringResource(R.string.gua_stickers_section_favourites)
    StickerSectionKind.Mine -> stringResource(R.string.gua_stickers_section_mine)
    is StickerSectionKind.Pack -> name
}

private fun StickerSection.key(): String = when (val kind = kind) {
    StickerSectionKind.Recent -> "recent"
    StickerSectionKind.Favourites -> "favourites"
    StickerSectionKind.Mine -> "mine"
    is StickerSectionKind.Pack -> "pack:${kind.eventType}:${kind.stateKey}"
}

private fun StickerSection.extraTiles(): Int = if (kind == StickerSectionKind.Recent) 1 else 0
