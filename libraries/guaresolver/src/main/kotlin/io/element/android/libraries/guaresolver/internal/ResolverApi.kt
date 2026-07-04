/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * GUA FORK: Retrofit surface for the Gua resolver `POST /resolve` endpoint. Internal to the module;
 * the public API only ever exposes [io.element.android.libraries.guaresolver.HomeserverResolution].
 */
internal interface ResolverApi {
    @POST("resolve")
    suspend fun resolve(@Body body: ResolveRequest): ResolveResponse
}

@Serializable
internal data class ResolveRequest(
    val phone: String,
)

@Serializable
internal data class HomeserverRef(
    val serverName: String,
    val baseUrl: String,
    val masIssuer: String? = null,
    val region: String? = null,
)

@Serializable
internal data class ResolveResponse(
    val exists: Boolean,
    val homeserver: HomeserverRef? = null,
    val registerAt: HomeserverRef? = null,
)
