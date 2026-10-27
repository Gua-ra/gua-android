/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import io.element.android.libraries.matrix.api.core.DeviceId

/**
 * Reads this account's devices from the server and sends the retry nudge. Every call reads the current access token, and any
 * failure, a 401 included, is returned as a failed result and never signs the session out.
 */
interface OwnDevicesClient {
    suspend fun devices(): Result<List<OwnDevice>>

    suspend fun deviceKeys(): Result<OwnDeviceKeys>

    /** Sends [RETRY_REQUEST_EVENT_TYPE] to [target], one of this account's devices. */
    suspend fun sendRetryRequest(target: DeviceId): Result<Unit>
}

data class OwnDevice(
    val deviceId: DeviceId,
    val lastSeenTs: Long?,
)

data class OwnDeviceKeys(
    /** Key ids of this account's self-signing key, empty when the account has none. */
    val selfSigningKeyIds: Set<String>,
    val devices: Map<DeviceId, OwnDeviceKeyInfo>,
)

data class OwnDeviceKeyInfo(
    /** Key ids of this account's keys that signed the device. */
    val ownSignatureKeyIds: Set<String>,
    val isDehydrated: Boolean,
)

class OwnDevicesRequestException(val httpCode: Int) : Exception("HTTP $httpCode")

/** Gua-only, unencrypted to-device event: "a verified device of yours now has your keys; send your verification request again". */
const val RETRY_REQUEST_EVENT_TYPE = "global.gua.verification.retry_request"
