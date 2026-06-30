/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.features.logout.api.LogoutUseCase
import io.element.android.features.logout.impl.oidc.IdpSessionCleaner
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import timber.log.Timber

@ContributesBinding(AppScope::class)
@Inject
class DefaultLogoutUseCase(
    private val sessionStore: SessionStore,
    private val matrixClientProvider: MatrixClientProvider,
    private val idpSessionCleaner: IdpSessionCleaner,
) : LogoutUseCase {
    override suspend fun logoutAll(ignoreSdkError: Boolean) {
        sessionStore.getAllSessions()
            .forEach { sessionData ->
                val sessionId = SessionId(sessionData.userId)
                // GUA FORK: capture the homeserver up front; after logout the session is gone from
                // the store, so we can no longer look it up to clear the IdP browser session.
                val homeserverUrl = sessionData.homeserverUrl
                Timber.d("Logging out sessionId: $sessionId")
                matrixClientProvider.getOrRestore(sessionId).fold(
                    onSuccess = { client ->
                        client.logout(userInitiated = true, ignoreSdkError = ignoreSdkError)
                        // GUA FORK: end the IdP (MAS) browser session so the next login with a
                        // different phone can't silently reuse this account. Best-effort.
                        idpSessionCleaner.clear(homeserverUrl)
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to get or restore MatrixClient for sessionId: $sessionId")
                    }
                )
            }
    }
}
