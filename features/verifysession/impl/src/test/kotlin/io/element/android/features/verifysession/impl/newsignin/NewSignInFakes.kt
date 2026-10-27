/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import io.element.android.features.securebackup.api.IdentityResetPendingStore
import io.element.android.features.securebackup.api.KeyStorageProvisioner
import io.element.android.libraries.matrix.api.core.DeviceId
import kotlinx.coroutines.flow.MutableStateFlow

class FakeOwnDevicesClient(
    var devicesResult: () -> Result<List<OwnDevice>> = { Result.success(emptyList()) },
    var deviceKeysResult: () -> Result<OwnDeviceKeys> = { Result.success(OwnDeviceKeys(selfSigningKeyIds = emptySet(), devices = emptyMap())) },
    var sendRetryRequestResult: (DeviceId) -> Result<Unit> = { Result.success(Unit) },
) : OwnDevicesClient {
    var devicesCallCount = 0
        private set
    val sendAttempts = mutableListOf<DeviceId>()

    override suspend fun devices(): Result<List<OwnDevice>> {
        devicesCallCount++
        return devicesResult()
    }

    override suspend fun deviceKeys(): Result<OwnDeviceKeys> = deviceKeysResult()

    override suspend fun sendRetryRequest(target: DeviceId): Result<Unit> {
        sendAttempts += target
        return sendRetryRequestResult(target)
    }
}

class InMemoryDismissedSignInStore(
    initial: Set<DeviceId> = emptySet(),
) : DismissedSignInStore {
    val dismissed = initial.toMutableSet()

    override suspend fun dismissedDevices(): Set<DeviceId> = dismissed.toSet()

    override suspend fun dismiss(deviceId: DeviceId) {
        dismissed += deviceId
    }
}

class FakeIdentityResetPendingStore(
    var pending: Boolean = false,
) : IdentityResetPendingStore {
    override fun isPending(): Boolean = pending

    override fun markPending() {
        pending = true
    }

    override fun clear() {
        pending = false
    }
}

class FakeKeyStorageProvisioner : KeyStorageProvisioner {
    override val isProvisioning = MutableStateFlow(false)

    override fun start() {
        isProvisioning.value = true
    }
}
