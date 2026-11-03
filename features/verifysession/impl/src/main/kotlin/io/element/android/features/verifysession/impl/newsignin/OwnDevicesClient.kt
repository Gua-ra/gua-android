/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import io.element.android.libraries.matrix.api.core.DeviceId

/** Each call reads the current access token, and a failure, even a 401, never signs the session out. */
interface OwnDevicesClient {
    suspend fun devices(): Result<List<OwnDevice>>

    suspend fun deviceKeys(): Result<OwnDeviceKeys>

    suspend fun sendRetryRequest(target: DeviceId): Result<Unit>
}

data class OwnDevice(
    val deviceId: DeviceId,
    val lastSeenTs: Long?,
)

data class OwnDeviceKeys(
    val selfSigningKeyIds: Set<String>,
    val devices: Map<DeviceId, OwnDeviceKeyInfo>,
)

data class OwnDeviceKeyInfo(
    val ownSignatureKeyIds: Set<String>,
    val isDehydrated: Boolean,
)

class OwnDevicesRequestException(val httpCode: Int) : Exception("HTTP $httpCode")

const val RETRY_REQUEST_EVENT_TYPE = "global.gua.verification.retry_request"
