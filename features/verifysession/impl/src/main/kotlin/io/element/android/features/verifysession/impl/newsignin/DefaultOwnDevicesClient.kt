/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.sessionstorage.api.SessionStore
import retrofit2.HttpException
import java.util.UUID

@ContributesBinding(SessionScope::class)
class DefaultOwnDevicesClient(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val retrofitFactory: RetrofitFactory,
) : OwnDevicesClient {
    private val userId: String get() = matrixClient.sessionId.value

    override suspend fun devices(): Result<List<OwnDevice>> = call { api, authorization ->
        api.devices(authorization).devices.map { OwnDevice(deviceId = DeviceId(it.deviceId), lastSeenTs = it.lastSeenTs) }
    }

    override suspend fun deviceKeys(): Result<OwnDeviceKeys> = call { api, authorization ->
        val response = api.queryKeys(authorization, KeysQueryRequest(deviceKeys = mapOf(userId to emptyList())))
        OwnDeviceKeys(
            selfSigningKeyIds = response.selfSigningKeys[userId]?.keys.orEmpty().keys.filterTo(mutableSetOf()) { it.startsWith(ED25519_PREFIX) },
            devices = response.deviceKeys[userId].orEmpty().entries.associate { (deviceId, keys) ->
                DeviceId(deviceId) to OwnDeviceKeyInfo(
                    ownSignatureKeyIds = keys.signatures[userId].orEmpty().keys,
                    isDehydrated = keys.dehydrated == true,
                )
            },
        )
    }

    override suspend fun sendRetryRequest(target: DeviceId): Result<Unit> = call { api, authorization ->
        api.sendToDevice(
            authorization = authorization,
            eventType = RETRY_REQUEST_EVENT_TYPE,
            txnId = "gua-retry-${UUID.randomUUID()}",
            body = SendToDeviceRequest(
                messages = mapOf(userId to mapOf(target.value to RetryRequestContent(fromDevice = matrixClient.deviceId.value))),
            ),
        )
    }

    // The client calls bypass the logging interceptors because they carry the access token.
    private suspend fun <T> call(block: suspend (OwnDevicesApi, String) -> T): Result<T> = runCatchingExceptions {
        val session = sessionStore.getSession(userId) ?: error("No stored session")
        val api = retrofitFactory.createUnlogged(session.homeserverUrl).create(OwnDevicesApi::class.java)
        try {
            block(api, "Bearer ${session.accessToken}")
        } catch (e: HttpException) {
            throw OwnDevicesRequestException(e.code())
        }
    }

    private companion object {
        const val ED25519_PREFIX = "ed25519:"
    }
}
