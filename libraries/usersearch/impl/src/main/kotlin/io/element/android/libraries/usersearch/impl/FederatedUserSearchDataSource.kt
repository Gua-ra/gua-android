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

/** Lookups run in parallel. Failures and timeouts are dropped, so one slow server cannot stall the search. */
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
