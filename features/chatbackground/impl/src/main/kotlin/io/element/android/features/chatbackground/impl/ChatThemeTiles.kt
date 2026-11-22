/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.messageFromOtherBackground
import io.element.android.libraries.matrix.ui.chatbackground.ChatBackgroundCanvas
import io.element.android.libraries.matrix.ui.chatbackground.outgoingBubbleColor

private val TILE_WIDTH = 88.dp
private val TILE_SHAPE = RoundedCornerShape(10.dp)

@Composable
internal fun ChatThemeSectionTitle(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(titleRes),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        style = ElementTheme.typography.fontBodyLgMedium,
        color = ElementTheme.colors.textPrimary,
    )
}

@Composable
internal fun ChatThemeTileRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun ChatThemeTile(
    theme: ChatTheme,
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    showsBubbles: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showsLabel: Boolean = true,
) {
    val bubbleDescription = if (showsBubbles) {
        stringResource(R.string.gua_chat_theme_bubble_a11y, stringResource(theme.bubble?.nameRes ?: R.string.gua_chat_theme_bubble_standard))
    } else {
        null
    }
    val description = listOfNotNull(label.takeUnless { showsLabel }, bubbleDescription).joinToString(", ")
    Column(
        modifier = modifier
            .width(TILE_WIDTH)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = isSelected, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(TILE_SHAPE)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ElementTheme.colors.borderInteractivePrimary else ElementTheme.colors.borderInteractiveSecondary,
                    shape = TILE_SHAPE,
                )
                .clearAndSetSemantics { if (description.isNotEmpty()) contentDescription = description },
        ) {
            ChatBackgroundCanvas(background = theme.background, modifier = Modifier.fillMaxSize())
            if (showsBubbles) {
                TileBubbles(bubble = theme.bubble)
            }
            if (isSelected) {
                SelectedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        if (showsLabel) {
            Text(
                text = label,
                style = ElementTheme.typography.fontBodySmRegular,
                color = if (enabled) ElementTheme.colors.textPrimary else ElementTheme.colors.textDisabled,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TileBubbles(bubble: ChatBubbleColor?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(14.dp)
                .background(ElementTheme.colors.messageFromOtherBackground, RoundedCornerShape(7.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .fillMaxWidth(0.65f)
                .height(14.dp)
                .background(bubble.outgoingBubbleColor(), RoundedCornerShape(7.dp)),
        )
    }
}

@Composable
internal fun SelectedBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = CompoundIcons.CheckCircleSolid(),
        contentDescription = null,
        tint = ElementTheme.colors.iconAccentPrimary,
        modifier = modifier
            .size(20.dp)
            .background(ElementTheme.colors.bgCanvasDefault, CircleShape),
    )
}

@Composable
internal fun RoundOptionButton(
    label: String,
    currentValue: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { stateDescription = currentValue }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, CircleShape),
        ) {
            content()
        }
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = if (enabled) ElementTheme.colors.textPrimary else ElementTheme.colors.textDisabled,
        )
    }
}
