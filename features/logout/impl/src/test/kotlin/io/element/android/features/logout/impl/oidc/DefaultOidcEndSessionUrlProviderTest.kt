/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl.oidc

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class DefaultOidcEndSessionUrlProviderTest {
    @Test
    fun `discovers the end_session_endpoint via the two well-known hops`() = runTest {
        val server = MockWebServer()
        val issuer = server.url("/issuer").toString().trimEnd('/')
        // 1st hop: matrix client well-known -> issuer.
        server.enqueue(
            MockResponse().setBody(
                """
                { "org.matrix.msc2965.authentication": { "issuer": "$issuer" } }
                """.trimIndent()
            )
        )
        // 2nd hop: OpenID configuration -> end_session_endpoint.
        server.enqueue(
            MockResponse().setBody(
                """
                { "end_session_endpoint": "$issuer/logout" }
                """.trimIndent()
            )
        )

        val url = createProvider().getEndSessionUrl(server.url("/").toString())

        assertThat(url).isEqualTo("$issuer/logout")
        // Verify the two requested paths.
        assertThat(server.takeRequest().path).isEqualTo("/.well-known/matrix/client")
        assertThat(server.takeRequest().path).isEqualTo("/issuer/.well-known/openid-configuration")
        server.shutdown()
    }

    @Test
    fun `returns null when there is no authentication issuer`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{}"))

        val url = createProvider().getEndSessionUrl(server.url("/").toString())

        assertThat(url).isNull()
        server.shutdown()
    }

    @Test
    fun `returns null when the provider does not advertise RP-initiated logout`() = runTest {
        val server = MockWebServer()
        val issuer = server.url("/issuer").toString().trimEnd('/')
        server.enqueue(
            MockResponse().setBody(
                """{ "org.matrix.msc2965.authentication": { "issuer": "$issuer" } }"""
            )
        )
        server.enqueue(MockResponse().setBody("{}"))

        val url = createProvider().getEndSessionUrl(server.url("/").toString())

        assertThat(url).isNull()
        server.shutdown()
    }

    @Test
    fun `returns null on a transport failure rather than throwing`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))

        val url = createProvider().getEndSessionUrl(server.url("/").toString())

        assertThat(url).isNull()
        server.shutdown()
    }

    private fun createProvider() = DefaultOidcEndSessionUrlProvider(
        retrofitFactory = RetrofitFactory(
            okHttpClient = { OkHttpClient.Builder().build() },
            json = { DefaultJsonProvider() },
        ),
    )
}
