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

/**
 * GUA FORK: names this build's own factor-enrollment redirect, `global.gua:/oidc` for the
 * production app, `global.gua.dev:/oidc` for the QA one and `global.gua.debug:/oidc` for a debug
 * build.
 *
 * The scheme is taken from [OAuthRedirectUrlProvider], which reads the same `login_redirect_scheme`
 * resource the OAuth intent filter in the manifest is declared with, so the two can never disagree:
 * one build-type resValue decides both what the app answers for and what it asks to be sent back
 * to. The `/oidc` path is the shape the identity service allowlists and the one iOS already sends,
 * and the intent filter matches on the scheme alone, so it catches this redirect as readily as the
 * sign-in one.
 */
@ContributesBinding(AppScope::class)
class DefaultEnrollmentRedirectProvider(
    private val oAuthRedirectUrlProvider: OAuthRedirectUrlProvider,
) : EnrollmentRedirectProvider {
    override fun provide(): String? {
        // The sign-in redirect is "<scheme>:/", so the scheme is everything before the colon.
        val scheme = oAuthRedirectUrlProvider.provide().substringBefore(':')
        if (scheme.isEmpty()) return null
        return "$scheme:$ENROLLMENT_REDIRECT_PATH"
    }

    private companion object {
        private const val ENROLLMENT_REDIRECT_PATH = "/oidc"
    }
}
