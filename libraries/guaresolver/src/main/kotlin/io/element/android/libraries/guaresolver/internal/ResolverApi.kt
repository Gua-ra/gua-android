/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import io.element.android.libraries.guaresolver.FederationRoster
import io.element.android.libraries.guaresolver.ResolverRoutingClaimsEnvelope
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * GUA FORK: Retrofit surface for the Gua resolver endpoints. Internal to the module; the public
 * API only ever exposes [io.element.android.libraries.guaresolver.HomeserverResolution] and
 * [FederationRoster].
 */
internal interface ResolverApi {
    @POST("resolve")
    suspend fun resolve(@Body body: ResolveRequest): ResolveResponse

    @GET("roster")
    suspend fun roster(): FederationRoster
}

@Serializable
internal data class ResolveRequest(
    val phone: String,
    val country: String? = null,
    val mccmnc: String? = null,
    val carrier: String? = null,
    val regionHint: String? = null,
    val affiliations: List<String>? = null,
    val attributes: Map<String, String>? = null,
    val routingClaims: ResolverRoutingClaimsEnvelope? = null,
    val trace: Boolean? = null,
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
    val trace: DecisionTrace? = null,
)

@Serializable
internal data class DecisionTrace(
    val source: String,
    val rule: String,
    val ruleId: String? = null,
    val reason: String? = null,
    val policyId: String? = null,
    val policyVersion: Long? = null,
    val delegatedZoneId: String? = null,
    val assignmentPolicy: String? = null,
    val homeserverId: String? = null,
)
