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

/** A root flow such as a bug report leaves [activeTarget] on Home, so the lifecycle must be RESUMED too. */
internal fun isShowingMainScreens(lifecycleState: Lifecycle.State, activeTarget: LoggedInFlowNode.NavTarget?): Boolean =
    lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        when (activeTarget) {
            LoggedInFlowNode.NavTarget.Home, is LoggedInFlowNode.NavTarget.Room -> true
            else -> false
        }

internal fun VerificationRequest.Incoming.ownDeviceIdOrNull(): DeviceId? = when (this) {
    is VerificationRequest.Incoming.OtherSession -> details.deviceId
    is VerificationRequest.Incoming.User -> null
}
