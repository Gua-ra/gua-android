/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.api

import io.element.android.libraries.matrix.api.core.DeviceId
import kotlinx.coroutines.flow.Flow

/**
 * Finds a recent, unverified sign-in of this account and asks that device to send its verification request again, so this
 * device receives it once it knows the new device's keys.
 */
interface NewSignInMonitor {
    /**
     * Checks while the app is in the foreground, until cancelled.
     * [isShowingMainScreens] is false while another flow covers the chat list and chats.
     */
    suspend fun run(isShowingMainScreens: Flow<Boolean>)

    /** Records that [deviceId], one of this account's devices, sent this device a verification request. */
    fun onVerificationRequestReceived(deviceId: DeviceId)
}
