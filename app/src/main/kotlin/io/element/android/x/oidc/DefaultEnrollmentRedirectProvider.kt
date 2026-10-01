/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.oidc

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.guaresolver.EnrollmentRedirectProvider
import io.element.android.libraries.matrix.api.auth.OAuthRedirectUrlProvider

/** This build's factor-enrollment redirect: the sign-in redirect scheme with the `/oidc` path the identity service allowlists. */
@ContributesBinding(AppScope::class)
class DefaultEnrollmentRedirectProvider(
    private val oAuthRedirectUrlProvider: OAuthRedirectUrlProvider,
) : EnrollmentRedirectProvider {
    override fun provide(): String? {
        val scheme = oAuthRedirectUrlProvider.provide().substringBefore(':')
        if (scheme.isEmpty()) return null
        return "$scheme:$ENROLLMENT_REDIRECT_PATH"
    }

    private companion object {
        private const val ENROLLMENT_REDIRECT_PATH = "/oidc"
    }
}
