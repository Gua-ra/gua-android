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
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun MediaStackRemovalDialog(
    stack: TimelineItemMediaStackContent,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = stack.members.size
    ConfirmationDialog(
        title = pluralStringResource(stack.kind.removalTitlePlural, count, count),
        content = stringResource(R.string.gua_media_stack_remove_subtitle),
        submitText = stringResource(CommonStrings.action_remove),
        destructiveSubmit = true,
        onSubmitClick = onSubmit,
        onDismiss = onDismiss,
    )
}
