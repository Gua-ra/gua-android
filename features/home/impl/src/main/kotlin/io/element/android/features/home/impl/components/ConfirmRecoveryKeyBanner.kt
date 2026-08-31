/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.components.Announcement
import io.element.android.libraries.designsystem.components.AnnouncementType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

@Composable
internal fun ConfirmRecoveryKeyBanner(
    onContinueClick: () -> Unit,
    onDismissClick: () -> Unit,
    // GUA FORK: owned by the presenter, not by this composable. As local state it was set on tap
    // and never cleared, so the NotYet outcome left the button reading "Setting up…" for good, and
    // on an empty room list nothing ever disposes this banner to reset it.
    isWorking: Boolean,
    modifier: Modifier = Modifier,
) {
    Announcement(
        modifier = modifier.roomListBannerPadding(),
        // GUA FORK: upstream asks the user to confirm a recovery key. Gua never shows one, so
        // this says what is actually wrong and what the button will do.
        title = stringResource(R.string.gua_encryption_repair_title),
        description = stringResource(R.string.gua_encryption_repair_message),
        type = AnnouncementType.Actionable(
            actionText = stringResource(
                if (isWorking) R.string.gua_encryption_repair_action_in_progress else R.string.gua_encryption_repair_action
            ),
            onActionClick = onContinueClick,
            onDismissClick = onDismissClick,
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun ConfirmRecoveryKeyBannerPreview() = ElementPreview {
    ConfirmRecoveryKeyBanner(
        onContinueClick = {},
        onDismissClick = {},
        isWorking = false,
    )
}

@PreviewsDayNight
@Composable
internal fun ConfirmRecoveryKeyBannerWorkingPreview() = ElementPreview {
    ConfirmRecoveryKeyBanner(
        onContinueClick = {},
        onDismissClick = {},
        isWorking = true,
    )
}
