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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.designsystem.chatbackground.LocalChatBackgroundActive
import io.element.android.libraries.designsystem.chatbackground.legibilityCapsule
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.messageFromOtherBackground
import io.element.android.libraries.matrix.ui.chatbackground.outgoingBubbleColor

/** [onPreviewSend], when given, plays the live wallpaper's send pulse; the outgoing bubble offers it to accessibility services. */
@Composable
internal fun ChatBackgroundSampleMessages(
    isOnBackground: Boolean,
    theme: ChatTheme,
    modifier: Modifier = Modifier,
    onPreviewSend: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalChatBackgroundActive provides isOnBackground) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.gua_chat_background_sample_today),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .legibilityCapsule(),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
            )
            SampleBubble(
                text = stringResource(R.string.gua_chat_background_sample_incoming),
                color = ElementTheme.colors.messageFromOtherBackground,
                modifier = Modifier.align(Alignment.Start),
            )
            val previewSendLabel = stringResource(R.string.gua_chat_theme_preview_send_a11y)
            SampleBubble(
                text = stringResource(R.string.gua_chat_background_sample_outgoing),
                color = theme.outgoingBubbleColor(),
                modifier = Modifier
                    .align(Alignment.End)
                    .then(
                        if (onPreviewSend == null) {
                            Modifier
                        } else {
                            Modifier.semantics(mergeDescendants = true) {
                                onClick(label = previewSendLabel) {
                                    onPreviewSend()
                                    true
                                }
                            }
                        }
                    ),
            )
        }
    }
}

@Composable
private fun SampleBubble(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .widthIn(max = 240.dp)
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
    }
}
