/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.user.UserPreferences
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.atomic.molecules.ComposerAlertMolecule
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithLargeHeight
import io.element.android.libraries.designsystem.text.toAnnotatedString
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun PreferencesRootView(
    state: PreferencesRootState,
    onBackClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onSecureBackupClick: () -> Unit,
    onManageAccountClick: (url: String) -> Unit,
    onLinkNewDeviceClick: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
    onSetupTwoStepVerification: () -> Unit,
    onChangePhoneNumber: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenUserProfile: (MatrixUser) -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)

    // Include pref from other modules
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = CommonStrings.common_settings),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        UserPreferences(
            modifier = Modifier.clickable {
                onOpenUserProfile(state.myUser)
            },
            matrixUser = state.myUser,
        )
        if (!state.isAccountPinSetup) {
            // GUA FORK: the nudge sets up two-step verification (account PIN), not the local app-lock,
            // so gate it on the account PIN status rather than isLockScreenPinSetup.
            SetupPinBanner(onSetupTwoStepVerification)
        }
        if (state.isMultiAccountEnabled) {
            MultiAccountSection(
                state = state,
                onAddAccountClick = onAddAccountClick,
            )
        } else {
            HorizontalDivider()
        }
        // User status will be added here
        // 'Account' section
        ManageAccountSection(
            state = state,
            onManageAccountClick = onManageAccountClick,
            onLinkNewDeviceClick = onLinkNewDeviceClick,
            onOpenBlockedUsers = onOpenBlockedUsers
        )
        // 'Manage my app' section
        ManageAppSection(
            state = state,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenLockScreenSettings = onOpenLockScreenSettings,
            onSetupTwoStepVerification = onSetupTwoStepVerification,
            onChangePhoneNumber = onChangePhoneNumber,
            onSecureBackupClick = onSecureBackupClick,
        )

        // General section
        GeneralSection(
            state = state,
            onOpenAbout = onOpenAbout,
            onOpenAnalytics = onOpenAnalytics,
            onOpenRageShake = onOpenRageShake,
            onOpenAdvancedSettings = onOpenAdvancedSettings,
            onOpenDeveloperSettings = onOpenDeveloperSettings,
            onOpenLabs = onOpenLabs,
            onSignOutClick = onSignOutClick,
            onDeactivateClick = onDeactivateClick,
        )
        // Version
        Footer(
            version = state.version,
            deviceId = state.deviceId,
            onClick = if (!state.showDeveloperSettings) {
                { state.eventSink(PreferencesRootEvent.OnVersionInfoClick) }
            } else {
                null
            }
        )
    }
}

@Composable
private fun ColumnScope.MultiAccountSection(
    state: PreferencesRootState,
    onAddAccountClick: () -> Unit,
) {
    HorizontalDivider(
        thickness = 8.dp,
        color = ElementTheme.colors.bgSubtleSecondary,
    )
    state.otherSessions.forEach { matrixUser ->
        MatrixUserRow(
            modifier = Modifier
                .clickable {
                    state.eventSink(PreferencesRootEvent.SwitchToSession(matrixUser.userId))
                }
                .padding(top = 2.dp, bottom = 2.dp, end = 8.dp),
            matrixUser = matrixUser,
            avatarSize = AvatarSize.AccountItem,
            verticalSpaceWidth = 16.dp,
        )
    }
    ListItem(
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
        headlineContent = {
            Text(stringResource(CommonStrings.common_add_another_account))
        },
        onClick = onAddAccountClick,
    )
    HorizontalDivider(
        thickness = 8.dp,
        color = ElementTheme.colors.bgSubtleSecondary,
    )
}

@Composable
private fun ColumnScope.ManageAppSection(
    state: PreferencesRootState,
    onOpenNotificationSettings: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
    onSetupTwoStepVerification: () -> Unit,
    onChangePhoneNumber: () -> Unit,
    onSecureBackupClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(id = R.string.screen_notification_settings_title)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Notifications())),
        onClick = onOpenNotificationSettings,
    )
    ListItem(
        headlineContent = {
            Text(
                stringResource(
                    id = if (state.isLockScreenPinSetup) {
                        CommonStrings.common_screen_lock
                    } else {
                        R.string.screen_preferences_set_up_pin_title
                    }
                )
            )
        },
        // GUA FORK: no subtitle — 2SV is more than a PIN, so we don't advertise a "6-digit PIN" here.
        supportingContent = null,
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Lock())),
        // GUA FORK: when no PIN is set this row offers account 2SV (6-digit), not the
        // local 4-digit app-lock. When an app-lock PIN exists it manages the screen lock.
        onClick = if (state.isLockScreenPinSetup) onOpenLockScreenSettings else onSetupTwoStepVerification,
    )
    // GUA FORK: Change phone number — re-bind the account to a new number.
    ListItem(
        headlineContent = { Text(stringResource(id = R.string.screen_change_phone_title)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Edit())),
        onClick = onChangePhoneNumber,
    )
    if (state.showSecureBackup) {
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.common_encryption)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Key())),
            trailingContent = ListItemContent.Badge.takeIf { state.showSecureBackupBadge },
            onClick = onSecureBackupClick,
        )
    }
    HorizontalDivider()
}

@Composable
private fun SetupPinBanner(
    onSetupTwoStepVerification: () -> Unit,
) {
    ComposerAlertMolecule(
        avatar = null,
        content = stringResource(id = R.string.screen_preferences_pin_nudge_message).toAnnotatedString(),
        onSubmitClick = onSetupTwoStepVerification,
        showIcon = true,
        submitText = stringResource(id = R.string.screen_preferences_pin_nudge_action),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ColumnScope.ManageAccountSection(
    state: PreferencesRootState,
    onManageAccountClick: (url: String) -> Unit,
    onLinkNewDeviceClick: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
) {
    state.accountManagementUrl?.let { url ->
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.action_manage_account_and_devices)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.UserProfile())),
            trailingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.PopOut())),
            onClick = { onManageAccountClick(url) },
        )
    }
    if (state.showLinkNewDevice) {
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.common_link_new_device)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Devices())),
            onClick = onLinkNewDeviceClick,
        )
    }
    if (state.showBlockedUsersItem) {
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.common_blocked_users)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Block())),
            onClick = onOpenBlockedUsers,
            trailingContent = ListItemContent.Text(state.nbOfBlockedUsers.toString()),
        )
    }
    if (state.accountManagementUrl != null || state.showLinkNewDevice || state.showBlockedUsersItem) {
        HorizontalDivider()
    }
}

@Composable
private fun ColumnScope.GeneralSection(
    state: PreferencesRootState,
    onOpenAbout: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(id = CommonStrings.common_advanced_settings)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Settings())),
        onClick = onOpenAdvancedSettings,
    )
    if (state.showLabsItem) {
        ListItem(
            headlineContent = { Text(stringResource(id = R.string.screen_labs_title)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Labs())),
            onClick = onOpenLabs,
        )
    }
    ListItem(
        headlineContent = { Text(stringResource(id = CommonStrings.common_about)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Info())),
        onClick = onOpenAbout,
    )
    if (state.canReportBug) {
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.common_report_a_problem)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.ChatProblem())),
            onClick = onOpenRageShake
        )
    }
    if (state.showAnalyticsSettings) {
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.common_analytics)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Chart())),
            onClick = onOpenAnalytics,
        )
    }
    HorizontalDivider()
    ListItem(
        headlineContent = { Text(stringResource(id = CommonStrings.action_signout)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Close())),
        style = ListItemStyle.Destructive,
        onClick = onSignOutClick,
    )
    if (state.canDeactivateAccount) {
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.action_delete_account)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Delete())),
            style = ListItemStyle.Destructive,
            onClick = onDeactivateClick,
        )
    }
    // Put developer settings at the end, so nothing bad happens if the user clicks 8 times to enable the entry
    if (state.showDeveloperSettings) {
        DeveloperPreferencesView(onOpenDeveloperSettings)
    }
}

@Composable
private fun ColumnScope.Footer(
    version: String,
    deviceId: DeviceId?,
    onClick: (() -> Unit)?,
) {
    val text = remember(version, deviceId) {
        buildString {
            append(version)
            if (deviceId != null) {
                append("\n")
                append(deviceId)
            }
        }
    }
    Text(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        textAlign = TextAlign.Center,
        text = text,
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

@Composable
private fun DeveloperPreferencesView(onOpenDeveloperSettings: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(id = CommonStrings.common_developer_options)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Code())),
        onClick = onOpenDeveloperSettings
    )
}

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewLightPreview(@PreviewParameter(PreferencesRootStateProvider::class) state: PreferencesRootState) =
    ElementPreviewLight(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewDarkPreview(@PreviewParameter(PreferencesRootStateProvider::class) state: PreferencesRootState) =
    ElementPreviewDark(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: PreferencesRootState) {
    PreferencesRootView(
        state = state,
        onBackClick = {},
        onAddAccountClick = {},
        onOpenAnalytics = {},
        onOpenRageShake = {},
        onOpenDeveloperSettings = {},
        onOpenAdvancedSettings = {},
        onOpenLabs = {},
        onOpenAbout = {},
        onSecureBackupClick = {},
        onManageAccountClick = {},
        onLinkNewDeviceClick = {},
        onOpenNotificationSettings = {},
        onOpenLockScreenSettings = {},
        onSetupTwoStepVerification = {},
        onChangePhoneNumber = {},
        onOpenUserProfile = {},
        onOpenBlockedUsers = {},
        onSignOutClick = {},
        onDeactivateClick = {},
    )
}
