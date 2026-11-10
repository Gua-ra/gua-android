/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.chatbackground.api.nameRes
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.ui.chatbackground.ChatBackgroundCanvas
import io.element.android.libraries.matrix.ui.chatbackground.outgoingBubbleColor

private val BUBBLE_SWATCHES = listOf(null) + ChatBubbleColor.entries

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatBubblePickerView(
    state: ChatThemeState,
    selection: ChatBubbleColor?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.gua_chat_theme_chat_bubble),
                navigationIcon = { BackButton(onClick = { state.eventSink(ChatThemeEvent.Cancel) }) },
            )
        },
        bottomBar = {
            Button(
                text = stringResource(R.string.gua_chat_theme_action_set),
                onClick = { state.eventSink(ChatThemeEvent.Save) },
                enabled = state.canChange && selection != state.current.bubble,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ChatThemePreviewCard(theme = state.shownCurrent.copy(bubble = selection), modifier = Modifier.padding(16.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BUBBLE_SWATCHES.forEach { bubble ->
                    BubbleSwatch(
                        bubble = bubble,
                        isSelected = bubble == selection,
                        enabled = state.canChange,
                        onClick = { state.eventSink(ChatThemeEvent.SelectBubble(bubble)) },
                    )
                }
            }
            Text(
                text = stringResource(selection?.nameRes ?: R.string.gua_chat_theme_bubble_standard),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BubbleSwatch(
    bubble: ChatBubbleColor?,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val name = stringResource(bubble?.nameRes ?: R.string.gua_chat_theme_bubble_standard)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ElementTheme.colors.borderInteractivePrimary else ElementTheme.colors.borderInteractiveSecondary,
                shape = CircleShape,
            )
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
            .padding(if (isSelected) 4.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bubble.outgoingBubbleColor(), CircleShape),
        )
        if (isSelected) {
            SelectedBadge()
        }
    }
}

@Composable
internal fun ChatWallpaperPickerView(
    state: ChatThemeState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.gua_chat_theme_wallpaper),
                navigationIcon = { BackButton(onClick = { state.eventSink(ChatThemeEvent.Cancel) }) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            ListItem(
                content = { Text(stringResource(R.string.gua_chat_theme_choose_photo)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Image())),
                enabled = state.canChange,
                onClick = { state.eventSink(ChatThemeEvent.ChoosePhoto) },
            )
            ListItem(
                content = { Text(stringResource(R.string.gua_chat_theme_no_wallpaper)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Block())),
                trailingContent = if (state.current.background == ChatBackground.None) ListItemContent.Icon(IconSource.Vector(CompoundIcons.Check())) else null,
                enabled = state.canChange,
                onClick = { state.eventSink(ChatThemeEvent.SelectWallpaper(ChatBackground.None)) },
            )
            ChatThemeSection.wallpaperSections.forEach { section ->
                ChatThemeSectionTitle(section.titleRes)
                ChatThemeTileRow {
                    section.themes.map { it.background }.forEach { background ->
                        ChatThemeTile(
                            theme = ChatTheme(background),
                            label = stringResource(background.nameRes),
                            isSelected = background == state.current.background,
                            enabled = state.canChange,
                            showsBubbles = false,
                            onClick = { state.eventSink(ChatThemeEvent.SelectWallpaper(background)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatThemePreviewCard(
    theme: ChatTheme,
    modifier: Modifier = Modifier,
) {
    val description = "${stringResource(R.string.gua_chat_background_preview_a11y)}, ${stringResource(theme.nameRes)}"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, RoundedCornerShape(16.dp))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        ChatBackgroundCanvas(background = theme.background, modifier = Modifier.matchParentSize())
        ChatBackgroundSampleMessages(isOnBackground = theme.background != ChatBackground.None, bubble = theme.bubble)
    }
}
