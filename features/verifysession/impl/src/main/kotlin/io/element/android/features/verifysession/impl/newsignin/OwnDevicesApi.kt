/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

internal interface OwnDevicesApi {
    @GET("_matrix/client/v3/devices")
    suspend fun devices(
        @Header("Authorization") authorization: String,
    ): DevicesResponse

    @POST("_matrix/client/v3/keys/query")
    suspend fun queryKeys(
        @Header("Authorization") authorization: String,
        @Body body: KeysQueryRequest,
    ): KeysQueryResponse

    @PUT("_matrix/client/v3/sendToDevice/{eventType}/{txnId}")
    suspend fun sendToDevice(
        @Header("Authorization") authorization: String,
        @Path("eventType") eventType: String,
        @Path("txnId") txnId: String,
        @Body body: SendToDeviceRequest,
    )
}

@Serializable
internal data class DevicesResponse(
    val devices: List<DeviceJson> = emptyList(),
)

@Serializable
internal data class DeviceJson(
    @SerialName("device_id") val deviceId: String,
    @SerialName("last_seen_ts") val lastSeenTs: Long? = null,
)

@Serializable
internal data class KeysQueryRequest(
    @SerialName("device_keys") val deviceKeys: Map<String, List<String>>,
)

@Serializable
internal data class KeysQueryResponse(
    @SerialName("device_keys") val deviceKeys: Map<String, Map<String, DeviceKeysJson>> = emptyMap(),
    @SerialName("self_signing_keys") val selfSigningKeys: Map<String, CrossSigningKeyJson> = emptyMap(),
)

@Serializable
internal data class DeviceKeysJson(
    val signatures: Map<String, Map<String, String>> = emptyMap(),
    val dehydrated: Boolean? = null,
)

@Serializable
internal data class CrossSigningKeyJson(
    val keys: Map<String, String> = emptyMap(),
)

@Serializable
internal data class SendToDeviceRequest(
    val messages: Map<String, Map<String, RetryRequestContent>>,
)

@Serializable
internal data class RetryRequestContent(
    @SerialName("from_device") val fromDevice: String,
)
