/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifRenditions
import io.element.android.features.expressions.impl.R
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import io.element.android.features.expressions.api.R as ApiR

internal val gifMoods = listOf(
    R.string.gua_expressions_word_haha,
    R.string.gua_expressions_word_love,
    R.string.gua_expressions_word_sad,
    R.string.gua_expressions_word_thumbs_up,
    R.string.gua_expressions_word_celebrate,
    R.string.gua_expressions_word_wow,
    R.string.gua_expressions_word_angry,
    R.string.gua_expressions_word_dance,
)

@Composable
internal fun GifsTabView(
    state: DefaultExpressionPanelState,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val content = state.gifContent) {
                GifContent.Unavailable -> CentredMessage(R.string.gua_gifs_unavailable)
                GifContent.Loading -> CircularProgressIndicator()
                GifContent.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CentredMessage(R.string.gua_gifs_load_failed)
                    Button(text = stringResource(CommonStrings.action_try_again), onClick = { state.panelSink(PanelEvent.RetryGifs) })
                }
                is GifContent.Loaded -> if (content.items.isEmpty()) {
                    CentredMessage(R.string.gua_gifs_none_found)
                } else {
                    GifGrid(content, state.favouriteGifIds, imageLoader, state.panelSink)
                }
            }
        }
        if (state.gifContent != GifContent.Unavailable) {
            GifsBottomBar(state)
        }
    }
}

@Composable
private fun CentredMessage(@StringRes message: Int) {
    Text(
        text = stringResource(message),
        modifier = Modifier.padding(16.dp),
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun GifGrid(
    content: GifContent.Loaded,
    favouriteIds: ImmutableSet<String>,
    imageLoader: ImageLoader,
    eventSink: (PanelEvent) -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()
    val currentContent by rememberUpdatedState(content)
    val currentEventSink by rememberUpdatedState(eventSink)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= currentContent.items.size - LOAD_MORE_THRESHOLD }
            .collect { currentEventSink(PanelEvent.LoadMoreGifs) }
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalItemSpacing = 4.dp,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(content.items, key = { index, item -> "$index:${item.id}" }) { _, item ->
            GifTile(item = item, isFavourite = item.id in favouriteIds, imageLoader = imageLoader, eventSink = eventSink)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GifTile(
    item: GifItem,
    isFavourite: Boolean,
    imageLoader: ImageLoader,
    eventSink: (PanelEvent) -> Unit,
) {
    val preview = item.preview ?: return
    val label = stringResource(ApiR.string.gua_a11y_gif, item.title.ifBlank { GifRenditions.DEFAULT_BODY })
    val favouriteLabel = stringResource(if (isFavourite) R.string.gua_stickers_remove_favourite else R.string.gua_stickers_add_favourite)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(preview.width.toFloat() / preview.height.coerceAtLeast(1))
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { eventSink(PanelEvent.OpenGif(item)) },
                onLongClick = { eventSink(PanelEvent.ToggleGifFavourite(item)) },
            )
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(favouriteLabel) {
                        eventSink(PanelEvent.ToggleGifFavourite(item))
                        true
                    }
                )
            },
    ) {
        GifImageView(preview, imageLoader, contentDescription = null, modifier = Modifier.fillMaxSize())
        if (isFavourite) {
            Icon(
                imageVector = CompoundIcons.FavouriteSolid(),
                contentDescription = null,
                tint = ElementTheme.colors.iconOnSolidPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
            )
        }
    }
}

@Composable
private fun GifsBottomBar(state: DefaultExpressionPanelState) {
    val isBrowsing = state.gifQuery.isBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrowseButton(GifBrowse.Recent, R.string.gua_stickers_section_recent, isBrowsing && state.gifBrowse == GifBrowse.Recent, state.panelSink)
        BrowseButton(GifBrowse.Favourites, R.string.gua_stickers_section_favourites, isBrowsing && state.gifBrowse == GifBrowse.Favourites, state.panelSink)
        BrowseButton(GifBrowse.Trending, R.string.gua_gifs_trending, isBrowsing && state.gifBrowse == GifBrowse.Trending, state.panelSink)
        gifMoods.forEach { mood ->
            val word = stringResource(mood)
            FilterChip(
                selected = state.gifQuery == word,
                onClick = { state.panelSink(PanelEvent.UpdateQuery(word)) },
                label = { Text(text = word, style = ElementTheme.typography.fontBodyMdRegular) },
                shape = CircleShape,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun BrowseButton(browse: GifBrowse, @StringRes title: Int, isSelected: Boolean, eventSink: (PanelEvent) -> Unit) {
    val label = stringResource(title)
    IconButton(
        onClick = { eventSink(PanelEvent.BrowseGifs(browse)) },
        modifier = Modifier.semantics {
            contentDescription = label
            selected = isSelected
        },
    ) {
        Icon(
            imageVector = when (browse) {
                GifBrowse.Recent -> CompoundIcons.Time()
                GifBrowse.Favourites -> CompoundIcons.Favourite()
                GifBrowse.Trending -> CompoundIcons.Chart()
            },
            contentDescription = null,
            tint = if (isSelected) ElementTheme.colors.iconAccentPrimary else ElementTheme.colors.iconSecondary,
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 6
