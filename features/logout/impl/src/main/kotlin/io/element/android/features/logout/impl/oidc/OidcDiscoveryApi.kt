/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl.oidc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * GUA FORK: tiny discovery client used by [OidcEndSessionUrlProvider] to find the MAS
 * `end_session_endpoint` from a homeserver, so logout can clear the IdP browser session.
 *
 * Two hops, both well-known documents fetched by absolute URL:
 *  1. `{homeserver}/.well-known/matrix/client` -> the MSC2965 authentication issuer.
 *  2. `{issuer}/.well-known/openid-configuration` -> the OpenID Provider metadata, which carries the
 *     `end_session_endpoint`.
 */
internal interface OidcDiscoveryApi {
    @GET
    suspend fun getMatrixClientWellKnown(@Url url: String): MatrixClientWellKnown

    @GET
    suspend fun getOpenIdConfiguration(@Url url: String): OpenIdProviderMetadata
}

/**
 * Subset of `/.well-known/matrix/client` we care about: the MSC2965 authentication block that points
 * at the OAuth/OIDC issuer (MAS).
 */
@Serializable
internal data class MatrixClientWellKnown(
    @SerialName("org.matrix.msc2965.authentication")
    val authentication: AuthenticationData? = null,
)

@Serializable
internal data class AuthenticationData(
    @SerialName("issuer")
    val issuer: String? = null,
)

/**
 * Subset of the OpenID Provider metadata (`/.well-known/openid-configuration`): just the RP-initiated
 * logout endpoint.
 */
@Serializable
internal data class OpenIdProviderMetadata(
    @SerialName("end_session_endpoint")
    val endSessionEndpoint: String? = null,
)
