/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun MediaStackRemovalDialog(
    stack: TimelineItemMediaStackContent,
    onSubmit: (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val count = stack.members.size
    TextFieldDialog(
        title = pluralStringResource(stack.kind.removalTitlePlural, count, count),
        content = stringResource(R.string.gua_media_stack_remove_subtitle),
        placeholder = stringResource(R.string.screen_room_confirm_removal_reason_placeholder),
        supportingText = stringResource(R.string.screen_room_confirm_removal_reason_supporting_text),
        value = null,
        submitText = stringResource(CommonStrings.action_remove),
        destructiveSubmit = true,
        onSubmit = onSubmit,
        onDismissRequest = onDismiss,
    )
}
