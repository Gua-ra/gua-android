/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Provisions key storage after an identity reset, in the background.
 *
 * A reset leaves recovery disabled, and Gua shows nobody a recovery key, so the app provisions key
 * storage itself. Until that lands the recovery state is still unhealthy and the setup banner is
 * still on screen, which is what [isProvisioning] is for: the banner shows the work.
 */
interface KeyStorageProvisioner {
    /** True while a post-reset provision is running, so the setup banner can show the work. */
    val isProvisioning: StateFlow<Boolean>

    /** Starts provisioning if it is not already running, and returns immediately. */
    fun start()
}
