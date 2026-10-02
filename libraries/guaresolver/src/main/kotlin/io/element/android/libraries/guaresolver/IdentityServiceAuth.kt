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
 * GUA FORK: runs one authenticated identity-service call with the session's own access token, and
 * gives an expired token a single chance to be refreshed before the user is told anything.
 *
 * Every screen that talks to the identity service must go through here rather than read a token and
 * send it. A MAS access token lives five minutes and the SDK refreshes it on its own schedule, so
 * any token a screen holds can already be expired by the time it is sent. The identity service then
 * validates it through the homeserver's whoami, gets nothing back and answers 401 with an empty
 * body, which used to reach the user as "Sorry, an error occurred" even though repeating the action
 * a minute later worked. That is the whole failure this exists to remove: QA watched the first
 * `reauth/start` of a change-number attempt answer 401 and the same action answer 202 fifty seconds
 * later, and reasonably filed the feature as broken.
 *
 * iOS does not carry this retry. It avoids the same failure only because it reads the token from the
 * live SDK session on every call, which is the read [MatrixClient.accessToken] now makes here too;
 * the retry is the part that also covers a token that is expired in the SDK itself.
 *
 * @param T whatever the identity-service call answers with.
 *
 * @param sessionStore the persisted session, read only as the fallback described below.
 *
 * @param call the identity-service call, given the token to authenticate with. It must surface an
 * HTTP 401 that carried no error code of its own as [ResolverError.Server] with status 401, which is
 * what [IdentityServiceClient] already does; a 401 that named a reason (an expired reauth token, a
 * spent challenge, a wrong code) has its own [ResolverError] and is never retried here, because
 * repeating it would refuse again and, worse, could spend an attempt the server meters.
 *
 * @return whatever [call] returned, or [ResolverError.NoSession] when this device holds no token at
 * all, or [ResolverError.SessionRefreshNeeded] when the call was still refused after the refresh.
 */
suspend fun <T> MatrixClient.withFreshAccessToken(
    sessionStore: SessionStore,
    call: suspend (accessToken: String) -> Result<T>,
): Result<T> {
    val token = currentAccessToken(sessionStore) ?: return Result.failure(ResolverError.NoSession)
    val result = call(token)
    if (!result.isUnauthorized()) return result

    // Exactly one retry, and the refresh is a single SDK call that never comes back through here,
    // so a session the server keeps refusing settles on the message below instead of looping.
    Timber.i("The identity service refused the session token; asking the SDK to refresh it")
    val refreshed = refreshAccessTokenIfExpired() ?: return Result.failure(ResolverError.NoSession)
    if (refreshed == token) {
        // Worth saying out loud: the token the second attempt is about to send is the one that was
        // just refused, so a refusal here is the server's verdict and not a stale credential.
        Timber.w("Nothing was refreshed; retrying with the same token")
    }
    val retried = call(refreshed)
    return if (retried.isUnauthorized()) Result.failure(ResolverError.SessionRefreshNeeded) else retried
}

/**
 * The token to authenticate with: the one the SDK is using, falling back to the persisted copy for a
 * client that cannot report its session. The store is only ever a copy the SDK writes back after a
 * refresh, so it is the staler of the two and must never be preferred.
 */
private suspend fun MatrixClient.currentAccessToken(sessionStore: SessionStore): String? =
    accessToken()?.takeIf { it.isNotEmpty() }
        ?: sessionStore.getSession(sessionId.value)?.accessToken?.takeIf { it.isNotEmpty() }

/**
 * True for the one refusal that a fresh token can fix: a 401 the identity service sent without an
 * error code, which is how it reports a token the homeserver would not vouch for.
 */
private fun Result<*>.isUnauthorized(): Boolean =
    (exceptionOrNull() as? ResolverError.Server)?.status == UNAUTHORIZED

private const val UNAUTHORIZED = 401
