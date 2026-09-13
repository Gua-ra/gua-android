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
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.Announcement
import io.element.android.libraries.designsystem.components.AnnouncementType
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * GUA FORK: warns that someone started recovering this account, with a way to cancel it. Renders
 * nothing while no recovery is live.
 *
 * Public, with no preview of its own, so that `GuaAccountRecoveryBannerVerifyTest` can record it
 * without adding a preview to the sharded screenshot set.
 */
@Composable
fun AccountRecoveryBanner(
    state: AccountRecoveryBannerState,
    modifier: Modifier = Modifier,
) {
    val pendingRecovery = state.pendingRecovery ?: return
    val finishableAfter = pendingRecovery.finishableAfter
    Announcement(
        modifier = modifier.roomListBannerPadding(),
        title = stringResource(R.string.gua_account_recovery_banner_title),
        description = if (finishableAfter != null) {
            stringResource(R.string.gua_account_recovery_banner_message_later, finishableAfter)
        } else {
            stringResource(R.string.gua_account_recovery_banner_message_now)
        },
        type = AnnouncementType.Actionable(
            actionText = stringResource(R.string.gua_account_recovery_banner_action),
            onActionClick = { state.eventSink(AccountRecoveryBannerEvent.CancelRecovery) },
            // Not dismissible: a warning about someone taking over the account stays up until
            // the recovery is cancelled or ends.
            onDismissClick = null,
            actionInProgress = state.cancelAction is AsyncAction.Loading,
        ),
    )
}

/** GUA FORK: asks before cancelling, since the owner may have started the recovery themselves. */
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
