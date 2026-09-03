/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.api

import kotlinx.coroutines.flow.StateFlow

/**
 * GUA FORK: provisions key storage after an identity reset, behind whatever the user is looking at.
 *
 * A reset leaves recovery disabled, and Gua shows nobody a recovery key, so the app has to provision
 * key storage itself. That used to happen with the user held on the destructive confirmation screen
 * until it finished, which is a wait with nothing to justify it: the reset has already landed by
 * then, and provisioning needs no input from anyone.
 *
 * Doing it in the background creates the opposite problem, which is what [isProvisioning] is for.
 * Until provisioning lands the recovery state is legitimately still unhealthy, so the setup banner
 * is still on screen, and with no sign of work it reads as an untouched call to action. People press
 * it, it completes around then, and the press looks like the thing that fixed the account.
 */
interface KeyStorageProvisioner {
    /** True while a post-reset provision is running, so the setup banner can show the work. */
    val isProvisioning: StateFlow<Boolean>

    /** Starts provisioning if it is not already running, and returns immediately. */
    fun start()
}
