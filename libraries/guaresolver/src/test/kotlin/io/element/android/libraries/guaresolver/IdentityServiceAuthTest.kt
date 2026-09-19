/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IdentityServiceAuthTest {
    @Test
    fun `a call that succeeds first time refreshes nothing`() = runTest {
        var refreshes = 0
        val client = aClient(token = A_TOKEN) {
            refreshes++
            A_FRESH_TOKEN
        }
        val tokensSent = mutableListOf<String>()

        val result = client.withFreshAccessToken(aSessionStore()) { token ->
            tokensSent += token
            Result.success("ok")
        }

        assertThat(result.getOrNull()).isEqualTo("ok")
        assertThat(tokensSent).containsExactly(A_TOKEN)
        assertThat(refreshes).isEqualTo(0)
    }

    @Test
    fun `a failure that is not an unauthorized one is surfaced as it is, with no refresh`() = runTest {
        var refreshes = 0
        val client = aClient(token = A_TOKEN) {
            refreshes++
            A_FRESH_TOKEN
        }
        val tokensSent = mutableListOf<String>()

        val result = client.withFreshAccessToken(aSessionStore()) { token ->
            tokensSent += token
            Result.failure<String>(ResolverError.InvalidPin)
        }

        assertThat(result.exceptionOrNull()).isEqualTo(ResolverError.InvalidPin)
        assertThat(tokensSent).containsExactly(A_TOKEN)
        assertThat(refreshes).isEqualTo(0)
    }

    @Test
    fun `a 401 that named a reason of its own is never retried`() = runTest {
        // A spent reauth token is a 401 too, and the server spends it whatever the outcome. Retrying
        // would refuse again and burn a metered attempt.
        var refreshes = 0
        val client = aClient(token = A_TOKEN) {
            refreshes++
            A_FRESH_TOKEN
        }
        var calls = 0

        val result = client.withFreshAccessToken(aSessionStore()) {
            calls++
            Result.failure<String>(ResolverError.InvalidReauthToken)
        }

        assertThat(result.exceptionOrNull()).isEqualTo(ResolverError.InvalidReauthToken)
        assertThat(calls).isEqualTo(1)
        assertThat(refreshes).isEqualTo(0)
    }

    @Test
    fun `an unauthorized call is refreshed and retried once with the new token`() = runTest {
        var refreshes = 0
        val client = aClient(token = A_TOKEN) {
            refreshes++
            A_FRESH_TOKEN
        }
        val tokensSent = mutableListOf<String>()

        val result = client.withFreshAccessToken(aSessionStore()) { token ->
            tokensSent += token
            if (token == A_FRESH_TOKEN) Result.success("ok") else Result.failure(unauthorized())
        }

        assertThat(result.getOrNull()).isEqualTo("ok")
        assertThat(tokensSent).containsExactly(A_TOKEN, A_FRESH_TOKEN).inOrder()
        assertThat(refreshes).isEqualTo(1)
    }

    @Test
    fun `a call refused after the refresh gives up, and says the session needed refreshing`() = runTest {
        var refreshes = 0
        val client = aClient(token = A_TOKEN) {
            refreshes++
            A_FRESH_TOKEN
        }
        var calls = 0

        val result = client.withFreshAccessToken(aSessionStore()) {
            calls++
            Result.failure<String>(unauthorized())
        }

        // The give-up is a message the user can act on, not the generic error, and the retry happened
        // exactly once: nothing here can loop.
        assertThat(result.exceptionOrNull()).isEqualTo(ResolverError.SessionRefreshNeeded)
        assertThat(calls).isEqualTo(2)
        assertThat(refreshes).isEqualTo(1)
    }

    @Test
    fun `a refresh that changes nothing still retries once, then gives up`() = runTest {
        val client = aClient(token = A_TOKEN) { A_TOKEN }
        val tokensSent = mutableListOf<String>()

        val result = client.withFreshAccessToken(aSessionStore()) { token ->
            tokensSent += token
            Result.failure<String>(unauthorized())
        }

        assertThat(result.exceptionOrNull()).isEqualTo(ResolverError.SessionRefreshNeeded)
        assertThat(tokensSent).containsExactly(A_TOKEN, A_TOKEN)
    }

    @Test
    fun `the token comes from the SDK, not from the staler persisted copy`() = runTest {
        val client = aClient(token = A_TOKEN) { A_FRESH_TOKEN }
        val tokensSent = mutableListOf<String>()

        client.withFreshAccessToken(aSessionStore(storedToken = A_STORED_TOKEN)) { token ->
            tokensSent += token
            Result.success(Unit)
        }

        assertThat(tokensSent).containsExactly(A_TOKEN)
    }

    @Test
    fun `the persisted copy is the fallback when the SDK reports no session`() = runTest {
        val client = aClient(token = null) { A_FRESH_TOKEN }
        val tokensSent = mutableListOf<String>()

        client.withFreshAccessToken(aSessionStore(storedToken = A_STORED_TOKEN)) { token ->
            tokensSent += token
            Result.success(Unit)
        }

        assertThat(tokensSent).containsExactly(A_STORED_TOKEN)
    }

    @Test
    fun `no token anywhere is reported without calling the identity service at all`() = runTest {
        val client = aClient(token = null) { A_FRESH_TOKEN }
        var calls = 0

        val result = client.withFreshAccessToken(aSessionStore(storedToken = "")) {
            calls++
            Result.success(Unit)
        }

        assertThat(result.exceptionOrNull()).isEqualTo(ResolverError.NoSession)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `a session that is gone by the time of the refresh is reported, not retried`() = runTest {
        val client = aClient(token = A_TOKEN) { null }
        var calls = 0

        val result = client.withFreshAccessToken(aSessionStore()) {
            calls++
            Result.failure<String>(unauthorized())
        }

        assertThat(result.exceptionOrNull()).isEqualTo(ResolverError.NoSession)
        assertThat(calls).isEqualTo(1)
    }

    private fun aClient(token: String?, onRefresh: () -> String?): MatrixClient = FakeMatrixClient(
        sessionId = A_SESSION_ID,
        accessTokenLambda = { token },
        refreshAccessTokenLambda = onRefresh,
    )

    private fun aSessionStore(storedToken: String = A_STORED_TOKEN): SessionStore = InMemorySessionStore(
        listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = storedToken)),
    )

    /** What the identity service answers for a token the homeserver would not vouch for: a bare 401. */
    private fun unauthorized() = ResolverError.Server(401)

    private companion object {
        const val A_TOKEN = "theTokenTheSdkHolds"
        const val A_FRESH_TOKEN = "theTokenAfterTheRefresh"
        const val A_STORED_TOKEN = "theTokenTheStoreHolds"
    }
}
