/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * The 'Account settings' section.
 *
 * GUA FORK: carries Gua's account rows (two-step verification, change phone number). Notifications, report a
 * problem and sign out live in the root view, in the groups Gua uses.
 */
@Composable
fun PreferencesAccountView(
    state: PreferencesAccountState,
    onManageAccountClick: (url: String) -> Unit,
    onSetupTwoStepVerification: () -> Unit,
    onChangePhoneNumber: () -> Unit,
    onLinkNewDeviceClick: () -> Unit,
    onModerationAndSafetyClick: () -> Unit,
    onSecureBackupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceCategory(
        modifier = modifier,
        title = stringResource(CommonStrings.common_account_settings),
        showTopDivider = false,
    ) {
        state.accountManagementUrl?.let { url ->
            ListItem(
                content = { Text(stringResource(id = CommonStrings.action_manage_account_and_devices)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.UserProfile())),
                trailingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.PopOut())),
                onClick = { onManageAccountClick(url) },
            )
        }
        ListItem(
            content = { Text(stringResource(id = R.string.screen_two_step_verification_title)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Lock())),
            onClick = onSetupTwoStepVerification,
        )
        ListItem(
            content = { Text(stringResource(id = R.string.screen_change_phone_title)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Edit())),
            onClick = onChangePhoneNumber,
        )
        if (state.showLinkNewDevice) {
            ListItem(
                content = { Text(stringResource(id = CommonStrings.common_link_new_device)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Devices())),
                onClick = onLinkNewDeviceClick,
            )
        }
        ListItem(
            content = { Text(stringResource(id = CommonStrings.common_moderation_and_safety)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Admin())),
            onClick = onModerationAndSafetyClick,
        )
        if (state.showSecureBackup) {
            ListItem(
                content = { Text(stringResource(id = CommonStrings.common_encryption)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Key())),
                trailingContent = ListItemContent.Badge.takeIf { state.showSecureBackupBadge },
                onClick = onSecureBackupClick,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun PreferencesAccountViewPreview(@PreviewParameter(PreferencesAccountStatePreviewParam::class) state: PreferencesAccountState) =
    ElementPreview {
        PreferencesAccountView(
            state = state,
            onManageAccountClick = {},
            onSetupTwoStepVerification = {},
            onChangePhoneNumber = {},
            onLinkNewDeviceClick = {},
            onModerationAndSafetyClick = {},
            onSecureBackupClick = {},
        )
    }
