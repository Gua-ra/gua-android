/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/** Never surfaced in the UI. */
data class ResolvedHomeserver(
    val serverName: String,
    val baseUrl: String,
    val masIssuer: String?,
    val region: String?,
)

data class HomeserverResolution(
    val exists: Boolean,
    val homeserver: ResolvedHomeserver,
    /** The roster version the resolver answered from, when it says; what a verifier attributes a placement to. */
    val rosterVersion: Long? = null,
)
