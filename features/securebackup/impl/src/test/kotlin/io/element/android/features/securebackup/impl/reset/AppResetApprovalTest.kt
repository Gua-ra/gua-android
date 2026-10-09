/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class AppResetApprovalTest {
    private val server = MockWebServer()

    @After
    fun shutDown() {
        server.shutdown()
    }

    @Test
    fun `a refused token is refreshed and the approval is sent again`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(204))

        val approved = approve(aMatrixClient())

        assertThat(approved).isTrue()
        val first = server.takeRequest()
        val retry = server.takeRequest()
        assertThat(first.method).isEqualTo("POST")
        assertThat(first.requestUrl?.encodedPath).isEqualTo(APP_APPROVAL_PATH)
        assertThat(first.requestUrl?.query).isNull()
        assertThat(first.getHeader("Authorization")).isEqualTo("Bearer $AN_ACCESS_TOKEN")
        assertThat(retry.getHeader("Authorization")).isEqualTo("Bearer $A_REFRESHED_ACCESS_TOKEN")
    }

    @Test
    fun `a token still refused after the refresh falls back to the approval page`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        assertThat(approve(aMatrixClient())).isFalse()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `a server without the endpoint falls back to the approval page with no refresh`() = runTest {
        var refreshes = 0
        server.enqueue(MockResponse().setResponseCode(404))

        val approved = approve(
            FakeMatrixClient(
                sessionId = A_SESSION_ID,
                accessTokenLambda = { AN_ACCESS_TOKEN },
                refreshAccessTokenLambda = {
                    refreshes++
                    A_REFRESHED_ACCESS_TOKEN
                },
            ),
        )

        assertThat(approved).isFalse()
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(refreshes).isEqualTo(0)
    }

    private suspend fun approve(matrixClient: FakeMatrixClient): Boolean = approveIdentityResetFromApp(
        approvalUrl = server.url("/account/reset-cross-signing?action=approve#top").toString(),
        matrixClient = matrixClient,
        sessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = AN_ACCESS_TOKEN))),
        okHttpClient = { OkHttpClient() },
    )

    /** An SDK whose refresh replaces the token it reports from then on. */
    private fun aMatrixClient(): FakeMatrixClient {
        var token = AN_ACCESS_TOKEN
        return FakeMatrixClient(
            sessionId = A_SESSION_ID,
            accessTokenLambda = { token },
            refreshAccessTokenLambda = {
                token = A_REFRESHED_ACCESS_TOKEN
                token
            },
        )
    }

    private companion object {
        const val AN_ACCESS_TOKEN = "anAccessToken"
        const val A_REFRESHED_ACCESS_TOKEN = "aRefreshedAccessToken"
    }
}
