/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.usersearch.impl

import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.guaresolver.FederatedUserSearch
import io.element.android.libraries.guaresolver.FederationRosterProvider
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.usersearch.api.UserListDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * GUA FORK: exact-handle matches for a bare username on the other homeservers of the Gua
 * federation, honouring each server's discoverability policy. When the query isn't a bare handle
 * or the roster is unavailable the search yields nothing, so it silently degrades to local-only.
 *
 * Each candidate `@handle:server` is resolved through the same profile lookup used when a full
 * address is typed. Lookups run in parallel; failures (unknown user, unreachable server) and
 * lookups exceeding the timeout are dropped silently, so one slow server can't stall the search.
 * Mirrors the iOS `UserDiscoveryService` federated fan-out.
 */
@Inject
class FederatedUserSearchDataSource(
    private val client: MatrixClient,
    private val dataSource: UserListDataSource,
    private val rosterProvider: FederationRosterProvider,
) {
    suspend fun search(query: String): List<MatrixUser> {
        val handle = FederatedUserSearch.bareHandle(query) ?: return emptyList()
        val roster = rosterProvider.currentRoster() ?: return emptyList()
        val ownServerName = client.sessionId.domainName.orEmpty()
        val candidates = FederatedUserSearch.candidates(
            handle = handle,
            roster = roster,
            ownServerName = ownServerName,
        )
        if (candidates.isEmpty()) return emptyList()

        return supervisorScope {
            candidates
                .map { userId ->
                    async {
                        withTimeoutOrNull(LOOKUP_TIMEOUT) {
                            tryOrNull { dataSource.getProfile(UserId(userId)) }
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    companion object {
        private val LOOKUP_TIMEOUT = 3.seconds
    }
}
