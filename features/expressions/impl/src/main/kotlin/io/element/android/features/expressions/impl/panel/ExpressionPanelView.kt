/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.expressions.api.panel.ExpressionPanelEvent
import io.element.android.features.expressions.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.SearchField
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.emoji.api.picker.EmojiPickerRenderer
import io.element.android.libraries.emoji.api.picker.NoOpEmojiPickerRenderer
import kotlinx.collections.immutable.persistentSetOf

private val searchChips = listOf(
    R.string.gua_expressions_word_hi,
    R.string.gua_expressions_word_haha,
    R.string.gua_expressions_word_love,
    R.string.gua_expressions_word_sad,
    R.string.gua_expressions_word_wow,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpressionPanelView(
    state: DefaultExpressionPanelState,
    heights: PanelHeights,
    emojiPickerRenderer: EmojiPickerRenderer,
    gifImageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var height by remember(heights) { mutableStateOf(heights.opening) }
    val dragState = rememberDraggableState { delta -> height = (height - with(density) { delta.toDp() }).coerceIn(0.dp, heights.expanded) }
    val searchInteractionSource = remember { MutableInteractionSource() }
    val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
    val isImeVisible = WindowInsets.isImeVisible
    var wasImeVisible by remember { mutableStateOf(isImeVisible) }
    // The keyboard coming back for the composer, not for the panel's own search, means the user went back to typing.
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && !wasImeVisible && !isSearchFocused) state.eventSink(ExpressionPanelEvent.Close)
        wasImeVisible = isImeVisible
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(ElementTheme.colors.bgCanvasDefault),
    ) {
        Grabber(
            isExpanded = height > heights.opening,
            onExpand = { height = heights.expanded },
            onCollapse = { height = heights.opening },
            modifier = Modifier.draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = {
                    val settled = heights.settle(height)
                    if (settled == null) state.eventSink(ExpressionPanelEvent.Close)
                    height = settled ?: heights.opening
                },
            ),
        )
        ExpressionTabs(selected = state.tab, onSelect = { state.panelSink(PanelEvent.SelectTab(it)) })
        when (state.tab) {
            ExpressionTab.Emoji -> state.emojiPickerState?.let { emojiPickerState ->
                emojiPickerRenderer.Render(
                    state = emojiPickerState,
                    onSelectEmoji = { state.panelSink(PanelEvent.InsertEmoji(it.unicode)) },
                    selectedEmojis = persistentSetOf(),
                    modifier = Modifier.weight(1f),
                )
            }
            ExpressionTab.Gif -> {
                if (state.gifContent != GifContent.Unavailable) {
                    key(ExpressionTab.Gif) { SearchHeader(state, stringResource(R.string.gua_expressions_search_klipy), searchInteractionSource) }
                }
                GifsTabView(state, gifImageLoader, Modifier.weight(1f))
            }
            ExpressionTab.Stickers -> {
                key(ExpressionTab.Stickers) { SearchHeader(state, stringResource(R.string.gua_expressions_search_stickers), searchInteractionSource) }
                StickersTabView(state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Grabber(
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandLabel = stringResource(R.string.gua_a11y_expressions_expand)
    val collapseLabel = stringResource(R.string.gua_a11y_expressions_collapse)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                val action = if (isExpanded) {
                    CustomAccessibilityAction(collapseLabel) {
                        onCollapse()
                        true
                    }
                } else {
                    CustomAccessibilityAction(expandLabel) {
                        onExpand()
                        true
                    }
                }
                customActions = listOf(action)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(ElementTheme.colors.iconQuaternary, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun ExpressionTabs(selected: ExpressionTab, onSelect: (ExpressionTab) -> Unit) {
    SecondaryTabRow(selectedTabIndex = selected.ordinal) {
        ExpressionTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = stringResource(
                            when (tab) {
                                ExpressionTab.Emoji -> R.string.gua_expressions_tab_emoji
                                ExpressionTab.Gif -> R.string.gua_expressions_tab_gif
                                ExpressionTab.Stickers -> R.string.gua_expressions_tab_stickers
                            }
                        ),
                        style = ElementTheme.typography.fontBodyMdMedium,
                    )
                },
            )
        }
    }
}

@Composable
private fun SearchHeader(state: DefaultExpressionPanelState, placeholder: String, interactionSource: MutableInteractionSource) {
    val fieldState = rememberTextFieldState(state.query)
    val query by rememberUpdatedState(state.query)
    val eventSink by rememberUpdatedState(state.panelSink)
    LaunchedEffect(state.query) {
        if (fieldState.text.toString() != state.query) fieldState.setTextAndPlaceCursorAtEnd(state.query)
    }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { if (it != query) eventSink(PanelEvent.UpdateQuery(it)) }
    }
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        SearchField(state = fieldState, placeholder = placeholder, interactionSource = interactionSource, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            searchChips.forEach { chip ->
                val word = stringResource(chip)
                FilterChip(
                    selected = state.query == word,
                    onClick = { state.panelSink(PanelEvent.UpdateQuery(word)) },
                    label = { Text(text = word, style = ElementTheme.typography.fontBodyMdRegular) },
                    shape = CircleShape,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ExpressionPanelViewPreview(@PreviewParameter(DefaultExpressionPanelStatePreviewParam::class) state: DefaultExpressionPanelState) = ElementPreview {
    val context = LocalContext.current
    ExpressionPanelView(
        state = state,
        heights = PanelHeights(opening = 480.dp, expanded = 480.dp),
        emojiPickerRenderer = NoOpEmojiPickerRenderer,
        gifImageLoader = remember { ImageLoader(context) },
    )
}
