/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * A homeserver as advertised by the Gua resolver: where a phone's account lives (login) or should be
 * created (register). The client configures OIDC against [baseUrl] and discovers the MAS issuer
 * through well-known, as it would for any account provider. Never surfaced in the UI.
 */
data class ResolvedHomeserver(
    val serverName: String,
    val baseUrl: String,
    val masIssuer: String?,
    val region: String?,
)

/** Outcome of resolving a phone number against the Gua resolver. */
data class HomeserverResolution(
    /** `true` when an account already exists for this phone (login), `false` to register. */
    val exists: Boolean,
    /** The homeserver to authenticate against (login) or create the account on (register). */
    val homeserver: ResolvedHomeserver,
)
