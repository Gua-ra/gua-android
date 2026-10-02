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
    // GUA FORK: owned by the presenter so every outcome can clear it.
    isWorking: Boolean,
    modifier: Modifier = Modifier,
) {
    Announcement(
        modifier = modifier.roomListBannerPadding(),
        // GUA FORK: Gua never shows a recovery key, so the copy says what is wrong and what the button does.
        title = stringResource(R.string.gua_encryption_repair_title),
        description = stringResource(R.string.gua_encryption_repair_message),
        type = AnnouncementType.Actionable(
            // GUA FORK: the label stays put and the button itself shows the work.
            actionText = stringResource(R.string.gua_encryption_repair_action),
            onActionClick = onContinueClick,
            onDismissClick = onDismissClick,
            actionInProgress = isWorking,
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
