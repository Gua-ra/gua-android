/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import kotlinx.serialization.Serializable

@Serializable
data class FederationRosterServer(
    val serverName: String,
    /** Absent means globally discoverable. */
    val searchVisibility: String? = null,
    val searchGroups: List<String>? = null,
)

@Serializable
data class FederationRosterEntry(
    val homeserver: FederationRosterServer,
    val status: String,
) {
    val isActive: Boolean
        get() = status == "ACTIVE"
}

@Serializable
data class FederationRoster(
    val entries: List<FederationRosterEntry> = emptyList(),
)
