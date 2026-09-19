/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.guaresolver.FakeGuaDeployment
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class DefaultFederationRosterFetcherTest {
    @Test
    fun `roster decoding ignores unknown fields and keeps the search policy`() = runTest {
        val server = MockWebServer()
        // A realistic resolver payload: the client only decodes what it needs and ignores the rest.
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "version": 12,
                  "generatedAt": "2026-08-14T12:00:00Z",
                  "entries": [
                    {
                      "homeserver": {
                        "id": "hs-br",
                        "serverName": "br.gua.example",
                        "baseUrl": "https://matrix.br.gua.example",
                        "masIssuer": "https://auth.br.gua.example",
                        "region": "br",
                        "weight": 100,
                        "acceptsNew": true,
                        "signingKey": "ed25519:aaaa"
                      },
                      "status": "ACTIVE"
                    },
                    {
                      "homeserver": {
                        "id": "hs-edu",
                        "serverName": "edu.gua.example",
                        "baseUrl": "https://matrix.edu.gua.example",
                        "signingKey": "ed25519:bbbb",
                        "searchVisibility": "GROUP",
                        "searchGroups": ["edu"]
                      },
                      "status": "ACTIVE"
                    },
                    {
                      "homeserver": {
                        "id": "hs-old",
                        "serverName": "old.gua.example",
                        "baseUrl": "https://matrix.old.gua.example",
                        "searchVisibility": "GLOBAL"
                      },
                      "status": "RETIRED"
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        val fetcher = createFetcher(server)

        val roster = fetcher.fetchRoster().getOrThrow()

        assertThat(roster.entries).hasSize(3)
        val first = roster.entries[0]
        assertThat(first.homeserver.serverName).isEqualTo("br.gua.example")
        assertThat(first.homeserver.searchVisibility).isNull()
        assertThat(first.homeserver.searchGroups).isNull()
        assertThat(first.isActive).isTrue()
        val second = roster.entries[1]
        assertThat(second.homeserver.serverName).isEqualTo("edu.gua.example")
        assertThat(second.homeserver.searchVisibility).isEqualTo("GROUP")
        assertThat(second.homeserver.searchGroups).containsExactly("edu")
        assertThat(roster.entries[2].isActive).isFalse()
        server.shutdown()
    }

    @Test
    fun `server error is surfaced with the status code`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        val fetcher = createFetcher(server)

        val result = fetcher.fetchRoster()

        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(ResolverError.Server::class.java)
        assertThat((error as ResolverError.Server).status).isEqualTo(503)
        server.shutdown()
    }

    @Test
    fun `malformed body is a transport error`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("not-json"))
        val fetcher = createFetcher(server)

        val result = fetcher.fetchRoster()

        assertThat(result.exceptionOrNull()).isInstanceOf(ResolverError.Transport::class.java)
        server.shutdown()
    }

    @Test
    fun `unconfigured resolver returns NotConfigured`() = runTest {
        val fetcher = DefaultFederationRosterFetcher(
            retrofitFactory = retrofitFactory(),
            deployment = FakeGuaDeployment(resolverBaseUrl = null, defaultAccountProvider = null),
        )

        val result = fetcher.fetchRoster()

        assertThat(result.exceptionOrNull()).isInstanceOf(ResolverError.NotConfigured::class.java)
    }

    private fun createFetcher(server: MockWebServer) = DefaultFederationRosterFetcher(
        retrofitFactory = retrofitFactory(),
        deployment = FakeGuaDeployment(resolverBaseUrl = server.url("/").toString()),
    )

    private fun retrofitFactory() = RetrofitFactory(
        okHttpClient = { OkHttpClient.Builder().build() },
        json = { DefaultJsonProvider() },
    )
}
