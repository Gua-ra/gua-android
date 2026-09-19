/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import kotlinx.serialization.Serializable

/**
 * GUA FORK: one homeserver in the resolver's signed federation roster (`GET /roster`). Only the
 * fields federated user search consumes are decoded; the rest of the entry (keys, weights, …) is
 * ignored. Android counterpart of iOS `FederationRosterServer`.
 */
@Serializable
data class FederationRosterServer(
    val serverName: String,
    /**
     * Raw bare-handle discoverability policy; absent means globally discoverable.
     * Interpreted by [RosterSearchVisibility].
     */
    val searchVisibility: String? = null,
    /** Discovery groups compared against the searcher's own server's groups when the policy is `group`. */
    val searchGroups: List<String>? = null,
)

/**
 * GUA FORK: a roster entry: a homeserver plus its membership status in the federation. Android
 * counterpart of iOS `FederationRosterEntry`.
 */
@Serializable
data class FederationRosterEntry(
    val homeserver: FederationRosterServer,
    val status: String,
) {
    val isActive: Boolean
        get() = status == "ACTIVE"
}

/**
 * GUA FORK: the resolver's view of the federation: every homeserver it routes to. Android
 * counterpart of iOS `FederationRoster`.
 */
@Serializable
data class FederationRoster(
    val entries: List<FederationRosterEntry> = emptyList(),
)
