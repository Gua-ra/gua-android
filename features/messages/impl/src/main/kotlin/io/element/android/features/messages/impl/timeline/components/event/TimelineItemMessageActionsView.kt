/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.actions.MessageAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.wysiwyg.link.Link
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun TimelineItemMessageActionsView(
    actions: ImmutableList<MessageAction>,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = IconSource.Vector(CompoundIcons.PopOut())
    // Intrinsic width keeps a short message's bubble narrow; the bubble then stretches the buttons to its own width.
    Column(modifier = modifier.width(IntrinsicSize.Max)) {
        actions.forEachIndexed { index, action ->
            val onClick = { onLinkClick(Link(url = action.url, text = action.label)) }
            if (index == 0) {
                Button(
                    text = action.label,
                    onClick = onClick,
                    size = ButtonSize.Medium,
                    leadingIcon = icon,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedButton(
                    text = action.label,
                    onClick = onClick,
                    size = ButtonSize.Medium,
                    leadingIcon = icon,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemMessageActionsViewPreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TimelineItemMessageActionsView(
            actions = persistentListOf(
                MessageAction(label = "View statement", url = "https://bills.example.org/statement"),
            ),
            onLinkClick = {},
        )
        TimelineItemMessageActionsView(
            actions = persistentListOf(
                MessageAction(label = "View statement", url = "https://bills.example.org/statement"),
                MessageAction(label = "Download the PDF", url = "https://bills.example.org/statement.pdf"),
                MessageAction(label = "Open the account settings of the service", url = "https://bills.example.org/settings"),
            ),
            onLinkClick = {},
        )
    }
}
