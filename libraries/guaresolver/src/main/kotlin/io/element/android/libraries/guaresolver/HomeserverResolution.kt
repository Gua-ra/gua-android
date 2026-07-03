/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: a homeserver as advertised by the Gua resolver — where a phone's account lives (login)
 * or should be created (register). The homeserver is identified by its Matrix `serverName`; the
 * client configures OIDC against the [baseUrl] and discovers the MAS issuer via well-known, exactly
 * as it would for any account provider. Mirrors iOS `ResolvedHomeserver`.
 *
 * The resolver output is never surfaced in the UI (homeserver abstraction, see the
 * `gua-abstract-matrix-details` convention).
 */
data class ResolvedHomeserver(
    val serverName: String,
    val baseUrl: String,
    val masIssuer: String?,
    val region: String?,
)

/**
 * GUA FORK: outcome of resolving a phone number against the Gua resolver. Mirrors iOS
 * `HomeserverResolution`.
 */
data class HomeserverResolution(
    /** `true` when an account already exists for this phone (-> login); `false` -> register. */
    val exists: Boolean,
    /** The homeserver to authenticate against (login) or create the account on (register). */
    val homeserver: ResolvedHomeserver,
)
