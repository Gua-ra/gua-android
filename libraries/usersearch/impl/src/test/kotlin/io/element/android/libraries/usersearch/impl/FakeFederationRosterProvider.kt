/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.usersearch.impl

import io.element.android.libraries.guaresolver.FederationRoster
import io.element.android.libraries.guaresolver.FederationRosterEntry
import io.element.android.libraries.guaresolver.FederationRosterProvider
import io.element.android.libraries.guaresolver.FederationRosterServer

/**
 * GUA FORK: fake [FederationRosterProvider] with a fixed roster (or none) for federated user
 * search tests.
 */
class FakeFederationRosterProvider(
    private val roster: FederationRoster? = null,
) : FederationRosterProvider {
    var callCount: Int = 0
        private set

    override suspend fun currentRoster(): FederationRoster? {
        callCount++
        return roster
    }
}

fun aFederationRoster(vararg serverNames: String) = FederationRoster(
    entries = serverNames.map { serverName ->
        FederationRosterEntry(
            homeserver = FederationRosterServer(serverName = serverName),
            status = "ACTIVE",
        )
    }
)
