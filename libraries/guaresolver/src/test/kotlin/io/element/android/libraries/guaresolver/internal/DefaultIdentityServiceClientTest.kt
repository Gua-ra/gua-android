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

class DefaultIdentityServiceClientTest {
    @Test
    fun `matches map to ContactMatch with homeserver-abstracted handle`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "matches": [
                    { "hashedPhone": "aaa", "userId": "@alice:gua.global", "username": "alice", "displayName": "Alice", "avatarUrl": "mxc://x/y" },
                    { "hashedPhone": "bbb", "userId": "@bob:matrix.gua.global" }
                  ]
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val matches = client.lookupContacts("token", listOf("aaa", "bbb")).getOrThrow()

        assertThat(matches).hasSize(2)
        assertThat(matches[0].displayHandle).isEqualTo("@alice")
        assertThat(matches[0].displayName).isEqualTo("Alice")
        assertThat(matches[0].avatarUrl).isEqualTo("mxc://x/y")
        // No username assigned -> strip the :homeserver suffix from the Matrix id.
        assertThat(matches[1].displayHandle).isEqualTo("@bob")
        server.shutdown()
    }

    @Test
    fun `request carries hashed phones and a bearer token but no raw numbers`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "matches": [] }"""))
        val client = createClient(server)

        client.lookupContacts("secret-token", listOf("hash1", "hash2")).getOrThrow()

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/directory/lookup")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        val body = request.body.readUtf8()
        assertThat(body).contains("hash1")
        assertThat(body).contains("hash2")
        // Privacy: only the hashed key is sent.
        assertThat(body).doesNotContain("+")
        server.shutdown()
    }

    @Test
    fun `empty input short-circuits without a network call`() = runTest {
        val client = DefaultIdentityServiceClient(
            retrofitFactory = retrofitFactory(),
            deployment = FakeGuaDeployment(identityServiceBaseUrl = null),
        )

        val result = client.lookupContacts("token", emptyList())

        assertThat(result.getOrThrow()).isEmpty()
    }

    @Test
    fun `server error is surfaced with the status code`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        val client = createClient(server)

        val result = client.lookupContacts("token", listOf("aaa"))

        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(ResolverError.Server::class.java)
        assertThat((error as ResolverError.Server).status).isEqualTo(503)
        server.shutdown()
    }

    @Test
    fun `unconfigured identity-service returns NotConfigured`() = runTest {
        val client = DefaultIdentityServiceClient(
            retrofitFactory = retrofitFactory(),
            deployment = FakeGuaDeployment(identityServiceBaseUrl = null),
        )

        val result = client.lookupContacts("token", listOf("aaa"))

        assertThat(result.exceptionOrNull()).isInstanceOf(ResolverError.NotConfigured::class.java)
    }

    @Test
    fun `startPasskeyEnrollment POSTs the start endpoint with a bearer token and parses enrollUrl`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{ "enrollUrl": "https://idp.gua.global/passkey/enroll?token=abc" }""")
        )
        val client = createClient(server)

        val enrollUrl = client.startPasskeyEnrollment("secret-token").getOrThrow()

        assertThat(enrollUrl).isEqualTo("https://idp.gua.global/passkey/enroll?token=abc")
        val request = server.takeRequest()
        // POST is the contract the identity service actually serves; asserting GET here is what
        // let the 405 ship.
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/security/passkey/enroll/start")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        server.shutdown()
    }

    // GUA FORK: account genesis registration (ADM-008 Phase 3).

    @Test
    fun `registerAccountGenesis POSTs the genesis and proof and parses the accountId and handle`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {
                  "accountId": "ga1aea6aqb5opmzmutench3ggzepkhgwmkajb3epqqrhckkf7bcbcwl2cy",
                  "attachHandle": "Zm9vYmFyYmF6cXV1eGNvcmdlZ3JhdWx0",
                  "expiresAt": "2026-09-11T12:00:00Z"
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val registration = client.registerAccountGenesis(genesisB64Url = "R1VBRw", proofB64Url = "c2ln").getOrThrow()

        assertThat(registration.accountId).isEqualTo("ga1aea6aqb5opmzmutench3ggzepkhgwmkajb3epqqrhckkf7bcbcwl2cy")
        assertThat(registration.attachHandle).isEqualTo("Zm9vYmFyYmF6cXV1eGNvcmdlZ3JhdWx0")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/account/genesis")
        // Self-authenticating: the proof inside the body is the credential, so no bearer token is sent.
        assertThat(request.getHeader("Authorization")).isNull()
        val body = request.body.readUtf8()
        assertThat(body).contains("\"genesis\":\"R1VBRw\"")
        assertThat(body).contains("\"proof\":\"c2ln\"")
        server.shutdown()
    }

    @Test
    fun `a 503 surfaces as a server error, which is how a deployment says it does no genesis`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{ "code": "genesis_disabled" }"""))
        val client = createClient(server)

        val error = client.registerAccountGenesis("R1VBRw", "c2ln").exceptionOrNull()

        assertThat(error).isEqualTo(ResolverError.Server(503))
        server.shutdown()
    }

    @Test
    fun `a 403 surfaces as a server error, which is how a deployment declines to issue`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{ "code": "genesis_issuance_not_permitted" }"""))
        val client = createClient(server)

        val error = client.registerAccountGenesis("R1VBRw", "c2ln").exceptionOrNull()

        assertThat(error).isEqualTo(ResolverError.Server(403))
        server.shutdown()
    }

    @Test
    fun `an unconfigured deployment never reaches the network`() = runTest {
        val client = DefaultIdentityServiceClient(
            retrofitFactory = retrofitFactory(),
            deployment = FakeGuaDeployment(identityServiceBaseUrl = null),
        )

        val error = client.registerAccountGenesis("R1VBRw", "c2ln").exceptionOrNull()

        assertThat(error).isInstanceOf(ResolverError.NotConfigured::class.java)
    }

    private fun createClient(server: MockWebServer) = DefaultIdentityServiceClient(
        retrofitFactory = retrofitFactory(),
        deployment = FakeGuaDeployment(identityServiceBaseUrl = server.url("/").toString()),
    )

    private fun retrofitFactory() = RetrofitFactory(
        okHttpClient = { OkHttpClient.Builder().build() },
        json = { DefaultJsonProvider() },
    )
}
