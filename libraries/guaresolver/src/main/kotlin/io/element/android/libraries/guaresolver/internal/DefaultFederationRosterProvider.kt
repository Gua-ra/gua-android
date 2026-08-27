/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.guaresolver.FederationRoster
import io.element.android.libraries.guaresolver.FederationRosterFetcher
import io.element.android.libraries.guaresolver.FederationRosterProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * GUA FORK: in-memory roster cache so a burst of searches doesn't hammer the resolver: the roster
 * only changes when servers join or leave the federation, so a short TTL is plenty. Keeps serving
 * the last good roster when a refresh fails. Mirrors iOS `FederationRosterCache`.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultFederationRosterProvider(
    private val fetcher: FederationRosterFetcher,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : FederationRosterProvider {
    private val mutex = Mutex()
    private var cachedRoster: FederationRoster? = null
    private var fetchedAtMs: Long = 0

    override suspend fun currentRoster(): FederationRoster? = mutex.withLock {
        val cached = cachedRoster
        if (cached != null && nowMs() - fetchedAtMs < TIME_TO_LIVE_MS) {
            return@withLock cached
        }
        fetcher.fetchRoster().fold(
            onSuccess = { roster ->
                cachedRoster = roster
                fetchedAtMs = nowMs()
                roster
            },
            onFailure = {
                // A transient resolver error shouldn't kill federated search: serve the stale roster if there is one.
                cached
            },
        )
    }

    companion object {
        private const val TIME_TO_LIVE_MS = 5 * 60_000L
    }
}
