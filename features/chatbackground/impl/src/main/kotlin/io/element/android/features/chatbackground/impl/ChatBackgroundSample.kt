/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.chatbackground.LocalChatBackgroundActive
import io.element.android.libraries.designsystem.chatbackground.legibilityCapsule
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.messageFromMeBackground
import io.element.android.libraries.designsystem.theme.messageFromOtherBackground

@Composable
internal fun ChatBackgroundSampleMessages(
    isOnBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalChatBackgroundActive provides isOnBackground) {
        Column(
            modifier = modifier.padding(horizontal = 12.dp, vertical = 16.dp),
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
            SampleBubble(
                text = stringResource(R.string.gua_chat_background_sample_outgoing),
                color = ElementTheme.colors.messageFromMeBackground,
                modifier = Modifier.align(Alignment.End),
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
