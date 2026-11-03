/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.api

import io.element.android.libraries.matrix.api.core.DeviceId
import kotlinx.coroutines.flow.Flow

/** Asks a recent, unverified sign-in of this account to send its verification request again. */
interface NewSignInMonitor {
    /** Suspends until cancelled. */
    suspend fun run(isShowingMainScreens: Flow<Boolean>)

    fun onVerificationRequestReceived(deviceId: DeviceId)
}
