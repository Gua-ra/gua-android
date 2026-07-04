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

    private fun createClient(server: MockWebServer) = DefaultResolverClient(
        retrofitFactory = retrofitFactory(),
        deployment = FakeGuaDeployment(resolverBaseUrl = server.url("/").toString()),
    )

    private fun retrofitFactory() = RetrofitFactory(
        okHttpClient = { OkHttpClient.Builder().build() },
        json = { DefaultJsonProvider() },
    )
}
