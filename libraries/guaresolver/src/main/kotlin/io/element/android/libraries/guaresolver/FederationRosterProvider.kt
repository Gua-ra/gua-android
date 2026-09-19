/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: provides the current federation roster to user search, or `null` when it isn't
 * available. Unavailability is not an error: federated search silently degrades to local-only.
 * Android counterpart of iOS `FederationRosterProviding`.
 */
interface FederationRosterProvider {
    suspend fun currentRoster(): FederationRoster?
}
