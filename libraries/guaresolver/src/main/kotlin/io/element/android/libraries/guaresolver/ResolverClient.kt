/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import kotlinx.serialization.Serializable

/**
 * GUA FORK: talks to the Gua resolver (`POST /resolve`) — the federation front door that maps a
 * phone number to a homeserver, so the client never hardcodes one. Android counterpart of iOS
 * `ResolverClientProtocol` / `ResolverClient`.
 */
interface ResolverClient {
    /**
     * Resolve a verified E.164 phone number to the homeserver it belongs to (login) or should be
     * created on (register).
     *
     * @param e164Phone the phone number in E.164 form (e.g. `+15551234567`).
     * @return [Result.success] with the [HomeserverResolution], or [Result.failure] with a
     * [ResolverError] (notably [ResolverError.NotConfigured] when no resolver URL is configured).
     */
    suspend fun resolve(e164Phone: String): Result<HomeserverResolution>

    /**
     * Resolve with additive v1 contract fields. Existing callers should keep using
     * [resolve] until they have verified identity/OIDC claims to transport.
     */
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
    /**
     * The E.164 phone this envelope was issued for. The resolver rejects an envelope whose subject
     * does not match the resolved phone, and the subject is part of the signed canonical bytes, so
     * a captured envelope cannot be replayed against another number.
     */
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
