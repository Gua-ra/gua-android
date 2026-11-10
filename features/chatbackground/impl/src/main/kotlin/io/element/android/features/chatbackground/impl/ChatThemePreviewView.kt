/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.Theme
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.ui.chatbackground.ChatBackgroundCanvas
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun ChatThemePreviewView(
    preview: ChatThemePreview,
    saveLabel: String,
    canChange: Boolean,
    eventSink: (ChatThemeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkPreview = preview.isDarkPreview ?: !ElementTheme.isLightTheme
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.gua_chat_theme_screen_title),
                navigationIcon = { BackButton(onClick = { eventSink(ChatThemeEvent.Cancel) }) },
            )
        },
        bottomBar = {
            PreviewControls(
                isDarkPreview = isDarkPreview,
                saveLabel = saveLabel,
                canChange = canChange,
                eventSink = eventSink,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
        ) {
            ElementTheme(theme = if (isDarkPreview) Theme.Dark else Theme.Light, applySystemBarsUpdate = false) {
                Box(modifier = Modifier.fillMaxSize().background(ElementTheme.colors.bgCanvasDefault)) {
                    ChatBackgroundCanvas(background = preview.shown.background, modifier = Modifier.fillMaxSize())
                    ChatBackgroundSampleMessages(
                        isOnBackground = preview.shown.background != ChatBackground.None,
                        bubble = preview.shown.bubble,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewControls(
    isDarkPreview: Boolean,
    saveLabel: String,
    canChange: Boolean,
    eventSink: (ChatThemeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgCanvasDefault)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                index = 0,
                count = 2,
                selected = !isDarkPreview,
                onClick = { eventSink(ChatThemeEvent.SetDarkPreview(false)) },
                text = stringResource(R.string.gua_chat_background_preview_light),
            )
            SegmentedButton(
                index = 1,
                count = 2,
                selected = isDarkPreview,
                onClick = { eventSink(ChatThemeEvent.SetDarkPreview(true)) },
                text = stringResource(R.string.gua_chat_background_preview_dark),
            )
        }
        Button(
            text = saveLabel,
            onClick = { eventSink(ChatThemeEvent.Save) },
            enabled = canChange,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            text = stringResource(CommonStrings.action_cancel),
            onClick = { eventSink(ChatThemeEvent.Cancel) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
