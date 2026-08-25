/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl.oidc

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.network.RetrofitFactory
import timber.log.Timber

/**
 * GUA FORK: resolves a homeserver to its MAS RP-initiated-logout (`end_session_endpoint`) URL.
 *
 * The Android login flow opens the MAS authorization page in a Chrome Custom Tab, which shares the
 * system browser's cookie jar. After signing out, that MAS session cookie survives, so the next
 * login silently reuses it and drops the user straight back into the PREVIOUS account (never
 * reaching the phone+OTP step). iOS sidesteps this with an *ephemeral* `ASWebAuthenticationSession`
 * at login; Custom Tabs have no ephemeral mode, so the Android analog is to actively END the IdP
 * session on logout by loading the MAS `end_session_endpoint` in a Custom Tab — which clears that
 * cookie in the same browser the next login will use.
 *
 * This provider only discovers the URL; [IdpSessionCleaner] opens it.
 */
interface OidcEndSessionUrlProvider {
    /**
     * Discover the `end_session_endpoint` for [homeserverUrl], or `null` if it cannot be resolved
     * (no MSC2965 issuer, no RP-initiated logout support, or a network/parsing failure). A `null`
     * result is non-fatal: logout still completes, the IdP cookie just isn't cleared.
     */
    suspend fun getEndSessionUrl(homeserverUrl: String): String?
}

@ContributesBinding(AppScope::class)
class DefaultOidcEndSessionUrlProvider(
    private val retrofitFactory: RetrofitFactory,
) : OidcEndSessionUrlProvider {
    override suspend fun getEndSessionUrl(homeserverUrl: String): String? {
        return runCatchingExceptions {
            val api = retrofitFactory
                .create(homeserverUrl.ensureProtocol())
                .create(OidcDiscoveryApi::class.java)

            val issuer = api
                .getMatrixClientWellKnown(wellKnownUrl(homeserverUrl))
                .authentication
                ?.issuer
                ?.takeIf { it.isNotBlank() }
                ?: return@runCatchingExceptions null

            api
                .getOpenIdConfiguration(openIdConfigurationUrl(issuer))
                .endSessionEndpoint
                ?.takeIf { it.isNotBlank() }
        }.onFailure {
            // Non-fatal: the homeserver/issuer is not surfaced in this log (Matrix server names are
            // safe to log per AGENTS.md, but we keep the message generic anyway).
            Timber.w(it, "Could not discover OIDC end_session_endpoint; skipping IdP session reset")
        }.getOrNull()
    }

    private fun wellKnownUrl(homeserverUrl: String): String =
        homeserverUrl.ensureProtocol().trimEnd('/') + "/.well-known/matrix/client"

    private fun openIdConfigurationUrl(issuer: String): String =
        issuer.ensureProtocol().trimEnd('/') + "/.well-known/openid-configuration"
}
