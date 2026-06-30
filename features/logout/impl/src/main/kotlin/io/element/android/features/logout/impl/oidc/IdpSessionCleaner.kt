/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl.oidc

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

/**
 * GUA FORK: clears the IdP (MAS) browser session on logout so a subsequent login with a DIFFERENT
 * phone number can't silently reuse the previous account.
 *
 * Android logs in via a Chrome Custom Tab, which shares the system browser's cookie jar. After
 * sign-out the MAS session cookie persists there, so MAS short-circuits the next authorization and
 * returns straight to whoever authenticated last. This is the analog of iOS' ephemeral
 * `ASWebAuthenticationSession` (which never persists that cookie in the first place): we discover
 * the homeserver's MAS RP-initiated-logout endpoint and load it in a Custom Tab, which clears the
 * cookie in the same browser the next login will use.
 *
 * Best-effort: failures (no issuer, no end_session support, no browser) are swallowed — logout still
 * completes locally, the IdP cookie just isn't cleared.
 */
interface IdpSessionCleaner {
    suspend fun clear(homeserverUrl: String)
}

@ContributesBinding(AppScope::class)
@Inject
class DefaultIdpSessionCleaner(
    @ApplicationContext private val context: Context,
    private val endSessionUrlProvider: OidcEndSessionUrlProvider,
) : IdpSessionCleaner {
    override suspend fun clear(homeserverUrl: String) {
        val endSessionUrl = endSessionUrlProvider.getEndSessionUrl(homeserverUrl)
        if (endSessionUrl == null) {
            Timber.d("No OIDC end_session_endpoint resolved; IdP session not cleared")
            return
        }
        runCatching {
            context.openUrlInChromeCustomTab(darkTheme = false, url = endSessionUrl)
        }.onFailure {
            Timber.w(it, "Failed to open IdP end_session_endpoint to clear the session")
        }
    }
}
