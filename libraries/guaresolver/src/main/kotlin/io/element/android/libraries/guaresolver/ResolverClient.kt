/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import kotlinx.serialization.Serializable

interface ResolverClient {
    suspend fun resolve(e164Phone: String): Result<HomeserverResolution>

    /** Resolve with the additive v1 contract fields. */
    suspend fun resolve(e164Phone: String, options: ResolverResolveOptions): Result<HomeserverResolution> =
        resolve(e164Phone)
}

@Serializable
data class ResolverResolveOptions(
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
data class ResolverRoutingClaimsEnvelope(
    val schemaVersion: String,
    val issuer: String,
    val audience: String,
    val issuedAt: String,
    val expiresAt: String,
    val nonce: String,
    /** The subject is part of the signed canonical bytes, so an envelope cannot be replayed against another number. */
    val subject: String,
    val affiliations: List<String>? = null,
    val attributes: Map<String, String>? = null,
    val signatures: List<ResolverClaimSignature>,
)

@Serializable
data class ResolverClaimSignature(
    val keyId: String,
    val signatureB64: String,
)
