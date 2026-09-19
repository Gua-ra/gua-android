/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: the slice of the resolver that federated user search needs: the roster of federation
 * homeservers (`GET /roster`). Android counterpart of iOS `FederationRosterFetching`.
 */
interface FederationRosterFetcher {
    /**
     * Fetch the current federation roster from the resolver.
     *
     * @return [Result.success] with the [FederationRoster], or [Result.failure] with a
     * [ResolverError] (notably [ResolverError.NotConfigured] when no resolver URL is configured).
     */
    suspend fun fetchRoster(): Result<FederationRoster>
}
