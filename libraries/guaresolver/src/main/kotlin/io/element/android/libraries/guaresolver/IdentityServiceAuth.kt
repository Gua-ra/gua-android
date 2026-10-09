/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import timber.log.Timber

/**
 * GUA FORK: runs an authenticated identity-service call with the token the SDK currently holds.
 *
 * A bare 401 (no error code) gets one SDK refresh and one retry. A 401 with its own error code, such
 * as a spent reauth token or a wrong code, is never retried, because the server meters those attempts.
 *
 * @return the call's result, [ResolverError.NoSession] when no token exists, or
 * [ResolverError.AccessTokenRefused] when the retry is refused too.
 */
suspend fun <T> MatrixClient.withFreshAccessToken(
    sessionStore: SessionStore,
    call: suspend (accessToken: String) -> Result<T>,
): Result<T> {
    val token = currentAccessToken(sessionStore) ?: return Result.failure(ResolverError.NoSession)
    val result = call(token)
    if (!result.isUnauthorized()) return result

    Timber.i("The identity service refused the access token; asking the SDK to refresh it")
    val refreshed = refreshAccessTokenIfExpired() ?: return Result.failure(ResolverError.NoSession)
    if (refreshed == token) {
        Timber.w("Nothing was refreshed; retrying with the same token")
    }
    val retried = call(refreshed)
    return if (retried.isUnauthorized()) Result.failure(ResolverError.AccessTokenRefused) else retried
}

/** The session store copy can lag behind a refresh, so it is only the fallback. */
private suspend fun MatrixClient.currentAccessToken(sessionStore: SessionStore): String? =
    accessToken()?.takeIf { it.isNotEmpty() }
        ?: sessionStore.getSession(sessionId.value)?.accessToken?.takeIf { it.isNotEmpty() }

/** The identity service answers a token the homeserver rejects with a 401 that has no error code. */
private fun Result<*>.isUnauthorized(): Boolean =
    (exceptionOrNull() as? ResolverError.Server)?.status == UNAUTHORIZED

private const val UNAUTHORIZED = 401
