/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.usersearch.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.usersearch.test.FakeUserListDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

private val SESSION_ID = SessionId("@current-user:br.gua.example")

class FederatedUserSearchDataSourceTest {
    @Test
    fun `bare handle fans out across the federation skipping the own server`() = runTest {
        val dataSource = FakeUserListDataSource()
        dataSource.givenProfileLookup { userId -> MatrixUser(userId = userId, displayName = "Ana") }
        val federatedDataSource = createDataSource(
            dataSource = dataSource,
            rosterProvider = FakeFederationRosterProvider(aFederationRoster("br.gua.example", "ca.gua.example", "pt.gua.example")),
        )

        val results = federatedDataSource.search("@Ana-Souza")

        assertThat(results.map { it.userId.value }).containsExactly(
            "@ana-souza:ca.gua.example",
            "@ana-souza:pt.gua.example",
        ).inOrder()
        assertThat(dataSource.getProfileCalls).doesNotContain(UserId("@ana-souza:br.gua.example"))
    }

    @Test
    fun `unknown users are dropped`() = runTest {
        val dataSource = FakeUserListDataSource()
        dataSource.givenProfileLookup { userId ->
            MatrixUser(userId = userId).takeIf { userId.value == "@ana-souza:pt.gua.example" }
        }
        val federatedDataSource = createDataSource(
            dataSource = dataSource,
            rosterProvider = FakeFederationRosterProvider(aFederationRoster("br.gua.example", "ca.gua.example", "pt.gua.example")),
        )

        val results = federatedDataSource.search("ana-souza")

        assertThat(results.map { it.userId.value }).containsExactly("@ana-souza:pt.gua.example")
    }

    @Test
    fun `lookup failures are dropped`() = runTest {
        val dataSource = FakeUserListDataSource()
        dataSource.givenProfileLookup { userId ->
            if (userId.value == "@ana-souza:ca.gua.example") error("unreachable server")
            MatrixUser(userId = userId)
        }
        val federatedDataSource = createDataSource(
            dataSource = dataSource,
            rosterProvider = FakeFederationRosterProvider(aFederationRoster("br.gua.example", "ca.gua.example", "pt.gua.example")),
        )

        val results = federatedDataSource.search("ana-souza")

        assertThat(results.map { it.userId.value }).containsExactly("@ana-souza:pt.gua.example")
    }

    @Test
    fun `slow lookup is dropped after the timeout`() = runTest {
        val dataSource = FakeUserListDataSource()
        dataSource.givenProfileLookup { userId ->
            if (userId.value == "@ana-souza:ca.gua.example") {
                delay(10.seconds)
            }
            MatrixUser(userId = userId)
        }
        val federatedDataSource = createDataSource(
            dataSource = dataSource,
            rosterProvider = FakeFederationRosterProvider(aFederationRoster("br.gua.example", "ca.gua.example", "pt.gua.example")),
        )

        val results = federatedDataSource.search("ana-souza")

        assertThat(results.map { it.userId.value }).containsExactly("@ana-souza:pt.gua.example")
    }

    @Test
    fun `non handle query does not fan out`() = runTest {
        val dataSource = FakeUserListDataSource()
        val rosterProvider = FakeFederationRosterProvider(aFederationRoster("br.gua.example", "ca.gua.example"))
        val federatedDataSource = createDataSource(dataSource = dataSource, rosterProvider = rosterProvider)

        val results = federatedDataSource.search("@ana-souza:ca.gua.example")

        assertThat(results).isEmpty()
        assertThat(rosterProvider.callCount).isEqualTo(0)
        assertThat(dataSource.getProfileCalls).isEmpty()
    }

    @Test
    fun `unavailable roster degrades to no federated results`() = runTest {
        val dataSource = FakeUserListDataSource()
        val federatedDataSource = createDataSource(
            dataSource = dataSource,
            rosterProvider = FakeFederationRosterProvider(roster = null),
        )

        val results = federatedDataSource.search("ana-souza")

        assertThat(results).isEmpty()
        assertThat(dataSource.getProfileCalls).isEmpty()
    }

    private fun createDataSource(
        dataSource: FakeUserListDataSource,
        rosterProvider: FakeFederationRosterProvider,
    ) = FederatedUserSearchDataSource(
        client = FakeMatrixClient(SESSION_ID),
        dataSource = dataSource,
        rosterProvider = rosterProvider,
    )
}
