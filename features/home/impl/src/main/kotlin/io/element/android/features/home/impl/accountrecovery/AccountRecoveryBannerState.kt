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

/**
 * What the banner can say about when this live recovery can be finished.
 *
 * Three cases rather than a nullable date, because "the server named no moment" and "the moment has
 * passed" are different facts and only the second one means the recovery can be finished now. The
 * fields decode with null defaults for older servers, so the first case is reachable, and collapsing
 * it into the second told the owner the takeover could be completed immediately when nothing said so.
 */
sealed interface PendingAccountRecovery {
    /**
     * [date] is the localised long DATE from which the recovery can be finished, with the year and
     * no time of day. The waits run in days, so an exact moment would be more precision than the
     * owner can use and more than the server should publish.
     */
    data class FinishableFrom(val date: String) : PendingAccountRecovery

    /** The moment the server named has passed, so the recovery can be finished right now. */
    data object FinishableNow : PendingAccountRecovery

    /** The server reported the recovery but no moment for it, so the banner claims no timing. */
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
