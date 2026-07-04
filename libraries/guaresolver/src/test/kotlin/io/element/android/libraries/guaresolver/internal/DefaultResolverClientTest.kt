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
import io.element.android.libraries.guaresolver.ResolverClaimSignature
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.guaresolver.ResolverResolveOptions
import io.element.android.libraries.guaresolver.ResolverRoutingClaimsEnvelope
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class DefaultResolverClientTest {
    @Test
    fun `existing user resolves to the login homeserver`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "exists": true,
                  "homeserver": { "serverName": "gua.global", "baseUrl": "https://matrix.gua.global", "masIssuer": "https://mas.gua.global", "region": "br" },
                  "registerAt": { "serverName": "register.gua.global", "baseUrl": "https://register.gua.global" }
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val result = client.resolve("+5511999999999")

        val resolution = result.getOrThrow()
        assertThat(resolution.exists).isTrue()
        assertThat(resolution.homeserver.baseUrl).isEqualTo("https://matrix.gua.global")
        assertThat(resolution.homeserver.masIssuer).isEqualTo("https://mas.gua.global")
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"phone":"+5511999999999"}""")
        server.shutdown()
    }

    @Test
    fun `new user resolves to the register host`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "exists": false,
                  "registerAt": { "serverName": "register.gua.global", "baseUrl": "https://register.gua.global" }
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val result = client.resolve("+15551234567")

        val resolution = result.getOrThrow()
        assertThat(resolution.exists).isFalse()
        assertThat(resolution.homeserver.baseUrl).isEqualTo("https://register.gua.global")
        server.shutdown()
    }

    @Test
    fun `missing matching ref is a malformed response`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "exists": true }"""))
        val client = createClient(server)

        val result = client.resolve("+15551234567")

        assertThat(result.exceptionOrNull()).isInstanceOf(ResolverError.MalformedResponse::class.java)
        server.shutdown()
    }

    @Test
    fun `server error is surfaced with the status code`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        val client = createClient(server)

        val result = client.resolve("+15551234567")

        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(ResolverError.Server::class.java)
        assertThat((error as ResolverError.Server).status).isEqualTo(503)
        server.shutdown()
    }

    @Test
    fun `unconfigured resolver returns NotConfigured`() = runTest {
        val client = DefaultResolverClient(
            retrofitFactory = retrofitFactory(),
            deployment = FakeGuaDeployment(resolverBaseUrl = null, defaultAccountProvider = null),
        )

        val result = client.resolve("+15551234567")

        assertThat(result.exceptionOrNull()).isInstanceOf(ResolverError.NotConfigured::class.java)
    }

    @Test
    fun `optional v1 routing fields are sent when provided`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "exists": false,
                  "registerAt": { "serverName": "institution.gua.global", "baseUrl": "https://institution.gua.global" },
                  "trace": { "source": "placement", "rule": "institution_domain", "homeserverId": "institution-br" }
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val result = client.resolve(
            "+5511999999999",
            ResolverResolveOptions(
                regionHint = "br-sp",
                affiliations = listOf("example.edu"),
                attributes = mapOf("oidc_issuer" to "https://sso.example.edu"),
                trace = true,
                routingClaims = ResolverRoutingClaimsEnvelope(
                    schemaVersion = "gua-routing-claims.v1",
                    issuer = "https://sso.example.edu",
                    audience = "gua-resolver",
                    issuedAt = "2026-07-04T12:00:00Z",
                    expiresAt = "2026-07-04T12:05:00Z",
                    nonce = "nonce-123",
                    affiliations = listOf("example.edu"),
                    attributes = mapOf("institution_domain" to "example.edu"),
                    signatures = listOf(ResolverClaimSignature(keyId = "sso-key-1", signatureB64 = "abc123")),
                ),
            )
        )

        assertThat(result.isSuccess).isTrue()
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains(""""phone":"+5511999999999"""")
        assertThat(body).contains(""""regionHint":"br-sp"""")
        assertThat(body).contains(""""trace":true""")
        assertThat(body).contains(""""routingClaims"""")
        assertThat(body).contains(""""nonce":"nonce-123"""")
        assertThat(body).contains(""""signatureB64":"abc123"""")
        server.shutdown()
    }

    private fun createClient(server: MockWebServer) = DefaultResolverClient(
        retrofitFactory = retrofitFactory(),
        deployment = FakeGuaDeployment(resolverBaseUrl = server.url("/").toString()),
    )

    private fun retrofitFactory() = RetrofitFactory(
        okHttpClient = { OkHttpClient.Builder().build() },
        json = { DefaultJsonProvider() },
    )
}
