/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The public, anonymous resolver artifacts a verifier fetches. Separate from `ResolverApi` so the roster
 * shape federated user search depends on stays as it is. `/policy/routing` answers 404 when no policy
 * is served, and the log endpoints are served by the authority node only.
 */
internal interface ResolverVerificationApi {
    @GET("roster")
    suspend fun roster(): SignedRoster

    @GET("roster/log")
    suspend fun log(): TransparencyLog

    @GET("roster/log/consistency")
    suspend fun consistency(@Query("first") first: Long, @Query("second") second: Long): ConsistencyProof

    @GET("policy/routing")
    suspend fun routingPolicy(): RoutingPolicyBundle
}
