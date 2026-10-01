/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.accountrecovery.AccountRecoveryBannerEvent
import io.element.android.features.home.impl.accountrecovery.AccountRecoveryBannerState
import io.element.android.features.home.impl.accountrecovery.PendingAccountRecovery
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.Announcement
import io.element.android.libraries.designsystem.components.AnnouncementType
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.ui.strings.CommonStrings

/** Public so `GuaAccountRecoveryBannerVerifyTest` can record it. */
@Composable
fun AccountRecoveryBanner(
    state: AccountRecoveryBannerState,
    modifier: Modifier = Modifier,
) {
    val pendingRecovery = state.pendingRecovery ?: return
    Announcement(
        modifier = modifier.roomListBannerPadding(),
        title = stringResource(R.string.gua_account_recovery_banner_title),
        description = when (pendingRecovery) {
            is PendingAccountRecovery.FinishableFrom ->
                stringResource(R.string.gua_account_recovery_banner_message_later, pendingRecovery.date)
            PendingAccountRecovery.FinishableNow ->
                stringResource(R.string.gua_account_recovery_banner_message_now)
            PendingAccountRecovery.FinishableUnknown ->
                stringResource(R.string.gua_account_recovery_banner_message_generic)
        },
        type = AnnouncementType.Actionable(
            actionText = stringResource(R.string.gua_account_recovery_banner_action),
            onActionClick = { state.eventSink(AccountRecoveryBannerEvent.CancelRecovery) },
            onDismissClick = null,
            actionInProgress = state.cancelAction is AsyncAction.Loading,
        ),
    )
}

@Composable
internal fun AccountRecoveryCancelConfirmation(
    state: AccountRecoveryBannerState,
) {
    if (!state.cancelAction.isConfirming()) return
    ConfirmationDialog(
        title = stringResource(R.string.gua_account_recovery_cancel_title),
        content = stringResource(R.string.gua_account_recovery_cancel_message),
        submitText = stringResource(R.string.gua_account_recovery_banner_action),
        cancelText = stringResource(CommonStrings.action_go_back),
        onSubmitClick = { state.eventSink(AccountRecoveryBannerEvent.ConfirmCancelRecovery) },
        onDismiss = { state.eventSink(AccountRecoveryBannerEvent.DismissCancelConfirmation) },
    )
}
