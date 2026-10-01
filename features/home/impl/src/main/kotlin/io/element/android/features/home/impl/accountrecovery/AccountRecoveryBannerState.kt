/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.accountrecovery

import io.element.android.libraries.architecture.AsyncAction

/** Never dismissible: stays up until the recovery is cancelled, finished or expires. */
data class AccountRecoveryBannerState(
    val pendingRecovery: PendingAccountRecovery?,
    val cancelAction: AsyncAction<Unit>,
    val eventSink: (AccountRecoveryBannerEvent) -> Unit,
)

/** "The server named no moment" and "the moment has passed" are distinct. Only the second means finishable now. */
sealed interface PendingAccountRecovery {
    data class FinishableFrom(val date: String) : PendingAccountRecovery

    data object FinishableNow : PendingAccountRecovery

    data object FinishableUnknown : PendingAccountRecovery
}

internal fun anAccountRecoveryBannerState(
    pendingRecovery: PendingAccountRecovery? = PendingAccountRecovery.FinishableFrom("6 April 2026"),
    cancelAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (AccountRecoveryBannerEvent) -> Unit = {},
) = AccountRecoveryBannerState(
    pendingRecovery = pendingRecovery,
    cancelAction = cancelAction,
    eventSink = eventSink,
)

internal fun aHiddenAccountRecoveryBannerState() = anAccountRecoveryBannerState(pendingRecovery = null)
