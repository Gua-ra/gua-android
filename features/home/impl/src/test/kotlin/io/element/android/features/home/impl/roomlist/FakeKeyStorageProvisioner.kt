/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import io.element.android.features.securebackup.api.KeyStorageProvisioner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeKeyStorageProvisioner(
    initiallyProvisioning: Boolean = false,
) : KeyStorageProvisioner {
    private val provisioning = MutableStateFlow(initiallyProvisioning)

    override val isProvisioning: StateFlow<Boolean> = provisioning

    var startCallCount = 0
        private set

    override fun start() {
        startCallCount++
        provisioning.value = true
    }

    fun finish() {
        provisioning.value = false
    }
}
