/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import androidx.lifecycle.Lifecycle
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.verification.VerificationRequest

/**
 * Whether the chat list or a chat is on screen. A flow pushed above the logged-in flow at the root, such as a bug report
 * or adding an account, leaves [activeTarget] on Home but moves the logged-in flow below RESUMED.
 */
internal fun isShowingMainScreens(lifecycleState: Lifecycle.State, activeTarget: LoggedInFlowNode.NavTarget?): Boolean =
    lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        when (activeTarget) {
            LoggedInFlowNode.NavTarget.Home, is LoggedInFlowNode.NavTarget.Room -> true
            else -> false
        }

/** The device the new sign-in monitor records for an incoming request: only requests from this account's own devices count. */
internal fun VerificationRequest.Incoming.ownDeviceIdOrNull(): DeviceId? = when (this) {
    is VerificationRequest.Incoming.OtherSession -> details.deviceId
    is VerificationRequest.Incoming.User -> null
}
