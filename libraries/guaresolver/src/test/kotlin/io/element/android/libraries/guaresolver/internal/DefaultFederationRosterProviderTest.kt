/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.guaresolver.FakeFederationRosterFetcher
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.guaresolver.aFederationRoster
import io.element.android.libraries.guaresolver.aFederationRosterEntry
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultFederationRosterProviderTest {
    private val rosterA = aFederationRoster(entries = listOf(aFederationRosterEntry(serverName = "a.gua.example")))
    private val rosterB = aFederationRoster(entries = listOf(aFederationRosterEntry(serverName = "b.gua.example")))

    @Test
    fun `roster is cached within the time to live`() = runTest {
        var now = 0L
        val fetcher = FakeFederationRosterFetcher { Result.success(rosterA) }
        val provider = DefaultFederationRosterProvider(fetcher = fetcher, nowMs = { now })

        val first = provider.currentRoster()
        fetcher.fetchRosterResult = { Result.success(rosterB) }
        now += 5 * 60_000L - 1
        val second = provider.currentRoster()

        assertThat(first).isEqualTo(rosterA)
        assertThat(second).isEqualTo(rosterA)
        assertThat(fetcher.fetchCount).isEqualTo(1)
    }

    @Test
    fun `roster refreshes after expiry`() = runTest {
        var now = 0L
        val fetcher = FakeFederationRosterFetcher { Result.success(rosterA) }
        val provider = DefaultFederationRosterProvider(fetcher = fetcher, nowMs = { now })

        val first = provider.currentRoster()
        fetcher.fetchRosterResult = { Result.success(rosterB) }
        now += 5 * 60_000L
        val second = provider.currentRoster()

        assertThat(first).isEqualTo(rosterA)
        assertThat(second).isEqualTo(rosterB)
        assertThat(fetcher.fetchCount).isEqualTo(2)
    }

    @Test
    fun `stale roster is served when the refresh fails`() = runTest {
        var now = 0L
        val fetcher = FakeFederationRosterFetcher { Result.success(rosterA) }
        val provider = DefaultFederationRosterProvider(fetcher = fetcher, nowMs = { now })

        val first = provider.currentRoster()
        fetcher.fetchRosterResult = { Result.failure(ResolverError.MalformedResponse) }
        now += 5 * 60_000L
        val second = provider.currentRoster()

        assertThat(first).isEqualTo(rosterA)
        assertThat(second).isEqualTo(rosterA)
        assertThat(fetcher.fetchCount).isEqualTo(2)
    }

    @Test
    fun `failure without a cached roster returns no roster`() = runTest {
        val fetcher = FakeFederationRosterFetcher { Result.failure(ResolverError.NotConfigured) }
        val provider = DefaultFederationRosterProvider(fetcher = fetcher)

        val roster = provider.currentRoster()

        assertThat(roster).isNull()
    }
}
