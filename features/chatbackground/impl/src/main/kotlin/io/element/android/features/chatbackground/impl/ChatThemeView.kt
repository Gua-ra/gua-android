/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.chatbackground.api.nameRes
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.ui.chatbackground.ChatBackgroundCanvas
import io.element.android.libraries.matrix.ui.chatbackground.outgoingBubbleColor
import io.element.android.libraries.chatbackground.api.R as ChatBackgroundR

@Composable
fun ChatThemeView(
    state: ChatThemeState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoDraft = state.photoDraft
    val preview = state.preview
    val page = state.page
    val cancel = { state.eventSink(ChatThemeEvent.Cancel) }
    when {
        photoDraft != null -> {
            BackHandler(onBack = cancel)
            ChatBackgroundPhotoPreviewView(
                draft = photoDraft,
                bubble = state.current.bubble,
                canChange = state.canChange,
                eventSink = state.eventSink,
                modifier = modifier,
            )
        }
        preview != null -> {
            BackHandler(onBack = cancel)
            ChatThemePreviewView(
                preview = preview,
                saveLabel = if (page == ChatThemePage.WallpaperPicker) stringResource(R.string.gua_chat_theme_action_set) else saveLabel(state.mode),
                canChange = state.canChange && preview.saved != state.current,
                eventSink = state.eventSink,
                modifier = modifier,
            )
        }
        page is ChatThemePage.BubblePicker -> {
            BackHandler(onBack = cancel)
            ChatBubblePickerView(state = state, selection = page.selection, modifier = modifier)
        }
        page == ChatThemePage.WallpaperPicker -> {
            BackHandler(onBack = cancel)
            ChatWallpaperPickerView(state = state, modifier = modifier)
        }
        else -> ChatThemeGalleryView(state = state, onBackClick = onBackClick, modifier = modifier)
    }
    AsyncActionView(
        async = state.action,
        onSuccess = {},
        onErrorDismiss = { state.eventSink(ChatThemeEvent.ClearError) },
        progressDialog = {
            AsyncActionViewDefaults.ProgressDialog(
                progressText = when (state.operation) {
                    ChatThemeOperation.PreparePhoto -> null
                    ChatThemeOperation.Save -> stringResource(R.string.gua_chat_theme_progress)
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
internal fun saveLabel(mode: ChatThemeMode): String = when (mode) {
    ChatThemeMode.Chat -> stringResource(R.string.gua_chat_theme_action_set_theme)
    ChatThemeMode.Default -> stringResource(R.string.gua_chat_theme_action_set_default)
}

@Composable
private fun ChatThemeGalleryView(
    state: ChatThemeState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.gua_chat_theme_screen_title),
                navigationIcon = { BackButton(onClick = onBackClick) },
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            ) {
                RoundOptionButton(
                    label = stringResource(R.string.gua_chat_theme_chat_bubble),
                    currentValue = stringResource(state.current.bubble?.nameRes ?: R.string.gua_chat_theme_bubble_standard),
                    enabled = state.canChange,
                    onClick = { state.eventSink(ChatThemeEvent.OpenBubblePicker) },
                ) {
                    Box(Modifier.fillMaxSize().background(state.current.bubble.outgoingBubbleColor()))
                }
                RoundOptionButton(
                    label = stringResource(R.string.gua_chat_theme_wallpaper),
                    currentValue = stringResource(state.shownCurrent.background.wallpaperNameRes),
                    enabled = state.canChange,
                    onClick = { state.eventSink(ChatThemeEvent.OpenWallpaperPicker) },
                ) {
                    ChatBackgroundCanvas(background = state.shownCurrent.background, modifier = Modifier.fillMaxSize())
                }
            }
            ChatThemeSection.entries.forEach { section ->
                ChatThemeSectionTitle(section.titleRes)
                ChatThemeTileRow {
                    if (section == ChatThemeSection.Featured) {
                        ChatThemeTile(
                            theme = state.defaultTile,
                            label = stringResource(
                                when (state.mode) {
                                    ChatThemeMode.Chat -> ChatBackgroundR.string.gua_chat_background_value_default
                                    ChatThemeMode.Default -> R.string.gua_chat_theme_classic
                                }
                            ),
                            isSelected = state.current == ChatTheme.STOCK,
                            enabled = state.canChange,
                            showsBubbles = true,
                            onClick = { state.eventSink(ChatThemeEvent.SelectDefault) },
                        )
                    }
                    section.themes.forEach { theme ->
                        ChatThemeTile(
                            theme = theme,
                            label = stringResource(theme.nameRes),
                            isSelected = theme == state.current,
                            enabled = state.canChange,
                            showsBubbles = true,
                            showsLabel = false,
                            onClick = { state.eventSink(ChatThemeEvent.SelectTheme(theme)) },
                        )
                    }
                }
            }
            ChatThemeNote(
                text = stringResource(
                    when (state.mode) {
                        ChatThemeMode.Chat -> R.string.gua_chat_theme_chat_note
                        ChatThemeMode.Default -> R.string.gua_chat_theme_default_note
                    }
                ),
            )
            if (state.hasPermission == false) {
                ChatThemeNote(text = stringResource(R.string.gua_chat_theme_no_permission))
            }
        }
    }
}

@get:StringRes
private val ChatBackground.wallpaperNameRes: Int
    get() = if (this == ChatBackground.None) R.string.gua_chat_theme_no_wallpaper else nameRes

@Composable
internal fun ChatThemeNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

@PreviewsDayNight
@Composable
internal fun ChatThemeViewPreview(@PreviewParameter(ChatThemeStatePreviewParam::class) state: ChatThemeState) = ElementPreview {
    ChatThemeView(
        state = state,
        onBackClick = {},
    )
}
