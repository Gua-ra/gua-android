/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.api

import kotlinx.coroutines.flow.StateFlow

interface KeyStorageProvisioner {
    /** True while a post-reset provision is running, so the setup banner can show the work. */
    val isProvisioning: StateFlow<Boolean>

    /** Starts provisioning if it is not already running, and returns immediately. */
    fun start()
}
