/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.accountrecovery

import io.element.android.libraries.architecture.AsyncAction

/**
 * GUA FORK: the warning shown while someone is recovering this account with the delayed recovery.
 *
 * Independent of the encryption banner and its dismissal, and never dismissible itself: it stays up
 * until the recovery is cancelled, finished or runs out.
 */
data class AccountRecoveryBannerState(
    /** The live recovery, or null when there is none and the banner is hidden. */
    val pendingRecovery: PendingAccountRecovery?,
    /** Confirming while the confirmation dialog is up, Loading while the cancel request runs. */
    val cancelAction: AsyncAction<Unit>,
    val eventSink: (AccountRecoveryBannerEvent) -> Unit,
)

data class PendingAccountRecovery(
    /**
     * The localised DATE after which the recovery can be finished, with no time of day, or null when
     * it can already be finished. The waits run in days, so an exact moment would be more precision
     * than the owner can use and more than the server should publish.
     */
    val finishableAfter: String?,
)

internal fun anAccountRecoveryBannerState(
    pendingRecovery: PendingAccountRecovery? = PendingAccountRecovery(finishableAfter = "Tuesday 6 April"),
    cancelAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (AccountRecoveryBannerEvent) -> Unit = {},
) = AccountRecoveryBannerState(
    pendingRecovery = pendingRecovery,
    cancelAction = cancelAction,
    eventSink = eventSink,
)

internal fun aHiddenAccountRecoveryBannerState() = anAccountRecoveryBannerState(pendingRecovery = null)
