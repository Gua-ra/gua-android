/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.guaresolver.withFreshAccessToken
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * GUA FORK: asks the server to open the reset window for this account, authenticated with the
 * session's own access token. False when the server does not offer this (an older deployment) or
 * refuses, in which case the approval page is the fallback.
 *
 * Blocks on the network, so callers run it on an IO dispatcher.
 */
internal suspend fun approveIdentityResetFromApp(
    approvalUrl: String,
    matrixClient: MatrixClient,
    sessionStore: SessionStore,
    okHttpClient: () -> OkHttpClient,
): Boolean {
    val endpoint = approvalUrl.toHttpUrlOrNull()
        ?.newBuilder()
        ?.encodedPath(APP_APPROVAL_PATH)
        ?.query(null)
        ?.fragment(null)
        ?.build()
        ?: return false
    // A 401 must surface as ResolverError.Server so the accessor refreshes and retries before the
    // approval page fallback.
    return matrixClient.withFreshAccessToken(sessionStore) { accessToken ->
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $accessToken")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        runCatchingExceptions { okHttpClient().newCall(request).execute().use { it.code } }
            .fold(
                onSuccess = { code ->
                    if (code in 200..299) {
                        Timber.d("Reset approved from the app's own session")
                        Result.success(Unit)
                    } else {
                        Timber.w("App-side approval answered $code")
                        Result.failure(ResolverError.Server(code))
                    }
                },
                onFailure = { Result.failure(ResolverError.Transport(it)) },
            )
    }
        .onFailure { Timber.w(it, "App-side approval failed; falling back to the approval page.") }
        .isSuccess
}

/** The server endpoint that opens the reset window for the caller's own account. */
internal const val APP_APPROVAL_PATH = "/api/gua/identity-reset/allow"
