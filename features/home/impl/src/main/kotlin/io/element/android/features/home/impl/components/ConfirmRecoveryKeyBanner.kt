/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    modifier: Modifier = Modifier,
) {
    // GUA FORK: the repair runs off-screen and can take a moment, so the button has to change the
    // instant it is pressed. Without this the product owner tapped it, saw nothing happen for
    // several seconds, and reasonably concluded the button was dead.
    var isWorking by remember { mutableStateOf(false) }

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
            onActionClick = {
                if (!isWorking) {
                    isWorking = true
                    onContinueClick()
                }
            },
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
    )
}
