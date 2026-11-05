/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.nameRes
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.ui.chatbackground.drawChatBackgroundFill
import io.element.android.libraries.matrix.ui.chatbackground.ChatBackgroundView as ChatBackgroundRenderer

private const val MAX_PRESETS_PER_ROW = 5
private const val MIN_PRESETS_PER_ROW = 3
private val PRESET_GAP = 12.dp
private val PRESET_PADDING = 2.dp
private val PRESET_MIN_WIDTH = 56.dp

@Composable
fun ChatBackgroundView(
    state: ChatBackgroundState,
    onBackClick: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.photoDraft
    if (draft != null) {
        BackHandler { state.eventSink(ChatBackgroundEvent.DiscardPhoto) }
        ChatBackgroundPhotoPreviewView(
            draft = draft,
            canSet = state.canChange,
            eventSink = state.eventSink,
            modifier = modifier,
        )
    } else {
        ChatBackgroundPicker(state = state, onBackClick = onBackClick, modifier = modifier)
    }
    AsyncActionView(
        async = state.action,
        onSuccess = { onDone() },
        onErrorDismiss = { state.eventSink(ChatBackgroundEvent.ClearError) },
        progressDialog = {
            AsyncActionViewDefaults.ProgressDialog(
                progressText = when (state.operation) {
                    ChatBackgroundOperation.PreparePhoto -> null
                    ChatBackgroundOperation.Set -> stringResource(R.string.gua_chat_background_progress)
                    ChatBackgroundOperation.Remove -> stringResource(R.string.gua_chat_background_progress_remove)
                }
            )
        },
        errorMessage = { error ->
            if (error is PhotoTooLargeException) {
                stringResource(R.string.gua_chat_background_error_too_large)
            } else {
                stringResource(R.string.gua_chat_background_error_generic)
            }
        },
    )
}

@Composable
private fun ChatBackgroundPicker(
    state: ChatBackgroundState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.gua_chat_background_screen_title),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    text = stringResource(R.string.gua_chat_background_action_set),
                    onClick = { state.eventSink(ChatBackgroundEvent.Apply) },
                    enabled = state.canSet,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.canRemove) {
                    TextButton(
                        text = stringResource(R.string.gua_chat_background_action_remove),
                        onClick = { state.eventSink(ChatBackgroundEvent.Remove) },
                        destructive = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ChatBackgroundPreviewCard(
                background = state.selection,
                modifier = Modifier.padding(16.dp),
            )
            PresetGrid(
                selected = (state.selection as? ChatBackground.Preset)?.preset,
                enabled = state.canChange,
                onSelect = { state.eventSink(ChatBackgroundEvent.SelectPreset(it)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            ListItem(
                content = { Text(stringResource(R.string.gua_chat_background_choose_photo)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Image())),
                enabled = state.canChange,
                onClick = { state.eventSink(ChatBackgroundEvent.ChoosePhoto) },
            )
            Text(
                text = stringResource(R.string.gua_chat_background_shared_note),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ChatBackgroundPreviewCard(
    background: ChatBackground,
    modifier: Modifier = Modifier,
) {
    val description = "${stringResource(R.string.gua_chat_background_preview_a11y)}, ${stringResource(background.nameRes)}"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, RoundedCornerShape(16.dp))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        ChatBackgroundRenderer(background = background)
        ChatBackgroundSampleMessages(isOnBackground = background != ChatBackground.None)
    }
}

@Composable
private fun PresetGrid(
    selected: ChatBackgroundPreset?,
    enabled: Boolean,
    onSelect: (ChatBackgroundPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelStyle = ElementTheme.typography.fontBodySmRegular
    val textMeasurer = rememberTextMeasurer()
    val names = ChatBackgroundPreset.entries.map { stringResource(it.nameRes) }
    BoxWithConstraints(modifier = modifier) {
        // As many columns as keep the longest name on one line in this language and font scale.
        val density = LocalDensity.current
        val longestName = remember(names, labelStyle, density) {
            with(density) { names.maxOf { textMeasurer.measure(it, labelStyle, maxLines = 1).size.width }.toDp() }
        }
        val cellWidth = max(longestName + PRESET_PADDING * 2, PRESET_MIN_WIDTH)
        val columns = ((maxWidth + PRESET_GAP) / (cellWidth + PRESET_GAP)).toInt().coerceIn(MIN_PRESETS_PER_ROW, MAX_PRESETS_PER_ROW)
        Column(verticalArrangement = Arrangement.spacedBy(PRESET_GAP)) {
            ChatBackgroundPreset.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(PRESET_GAP)) {
                    row.forEach { preset ->
                        PresetSwatch(
                            preset = preset,
                            isSelected = preset == selected,
                            enabled = enabled,
                            onClick = { onSelect(preset) },
                            labelStyle = labelStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PresetSwatch(
    preset: ChatBackgroundPreset,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = isSelected, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(PRESET_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(shape)
                .drawChatBackgroundFill(preset.fill(ElementTheme.isLightTheme))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ElementTheme.colors.borderInteractivePrimary else ElementTheme.colors.borderInteractiveSecondary,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = CompoundIcons.CheckCircleSolid(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconAccentPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .background(ElementTheme.colors.bgCanvasDefault, CircleShape),
                )
            }
        }
        Text(
            text = stringResource(preset.nameRes),
            style = labelStyle,
            color = if (enabled) ElementTheme.colors.textPrimary else ElementTheme.colors.textDisabled,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun ChatBackgroundViewPreview(@PreviewParameter(ChatBackgroundStatePreviewParam::class) state: ChatBackgroundState) = ElementPreview {
    ChatBackgroundView(
        state = state,
        onBackClick = {},
        onDone = {},
    )
}
