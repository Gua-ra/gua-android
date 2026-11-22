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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.Theme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.ui.chatbackground.ChatBackgroundCanvas
import io.element.android.libraries.matrix.ui.chatbackground.LiveWallpaperCues
import io.element.android.libraries.matrix.ui.chatbackground.liveWallpaperChatAreaEnd
import io.element.android.libraries.matrix.ui.chatbackground.rememberLiveWallpaperCanAnimate
import io.element.android.libraries.matrix.ui.chatbackground.rememberLiveWallpaperVisibleArea
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
    val chatArea = rememberLiveWallpaperVisibleArea()
    var previewSends by remember { mutableIntStateOf(0) }
    val artwork = (preview.shown.background as? ChatBackground.Art)?.artwork
    val onPreviewSend: (() -> Unit)? = if (artwork != null && rememberLiveWallpaperCanAnimate(artwork)) {
        { previewSends++ }
    } else {
        null
    }
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
                onPreviewSend = onPreviewSend,
                eventSink = eventSink,
                modifier = Modifier.liveWallpaperChatAreaEnd(chatArea),
            )
        },
    ) { padding ->
        // The chat's own frame, with the controls drawn over it like the composer, so an artwork is cropped exactly as in the chat.
        val framePadding = PaddingValues(top = padding.calculateTopPadding())
        Box(
            modifier = Modifier
                .padding(framePadding)
                .consumeWindowInsets(framePadding)
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
            ElementTheme(theme = if (isDarkPreview) Theme.Dark else Theme.Light, applySystemBarsUpdate = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ElementTheme.colors.bgCanvasDefault),
                ) {
                    ChatBackgroundCanvas(
                        background = preview.shown.background,
                        modifier = Modifier.fillMaxSize(),
                        live = LiveWallpaperCues(sendCount = previewSends),
                        visibleArea = chatArea,
                    )
                    ChatBackgroundSampleMessages(
                        isOnBackground = preview.shown.background != ChatBackground.None,
                        theme = preview.shown,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewControls(
    isDarkPreview: Boolean,
    saveLabel: String,
    canChange: Boolean,
    onPreviewSend: (() -> Unit)?,
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
        // Under large text the send button wraps below the toggle.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            LightDarkPreviewToggle(isDarkPreview = isDarkPreview, eventSink = eventSink, modifier = Modifier.weight(1f))
            if (onPreviewSend != null) {
                PreviewSendButton(onClick = onPreviewSend)
            }
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

/** Drawn like the composer's send button. */
@Composable
private fun PreviewSendButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(ElementTheme.colors.bgAccentRest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CompoundIcons.SendSolid(),
                contentDescription = stringResource(R.string.gua_chat_theme_preview_send_a11y),
                // The composer's optical centring of the arrow.
                modifier = Modifier.padding(start = 2.dp),
                tint = ElementTheme.colors.iconOnSolidPrimary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LightDarkPreviewToggle(
    isDarkPreview: Boolean,
    eventSink: (ChatThemeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
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
}
