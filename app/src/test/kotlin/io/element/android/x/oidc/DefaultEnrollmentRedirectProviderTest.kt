/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.oidc

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.auth.FakeOAuthRedirectUrlProvider
import org.junit.Test

class DefaultEnrollmentRedirectProviderTest {
    @Test
    fun `the enrollment redirect is this build's own sign-in scheme`() {
        val sut = DefaultEnrollmentRedirectProvider(
            oAuthRedirectUrlProvider = FakeOAuthRedirectUrlProvider(provideResult = "global.gua:/"),
        )

        assertThat(sut.provide()).isEqualTo("global.gua:/oidc")
    }

    @Test
    fun `the QA and debug builds name themselves, which is the whole point of sending it`() {
        val qa = DefaultEnrollmentRedirectProvider(
            oAuthRedirectUrlProvider = FakeOAuthRedirectUrlProvider(provideResult = "global.gua.dev:/"),
        )
        val debug = DefaultEnrollmentRedirectProvider(
            oAuthRedirectUrlProvider = FakeOAuthRedirectUrlProvider(provideResult = "global.gua.debug:/"),
        )

        // Without these the enrollment sheet returns to production's scheme, which on a QA device is
        // an app that is not installed.
        assertThat(qa.provide()).isEqualTo("global.gua.dev:/oidc")
        assertThat(debug.provide()).isEqualTo("global.gua.debug:/oidc")
    }

    @Test
    fun `a build with no scheme names nothing rather than an unusable redirect`() {
        val sut = DefaultEnrollmentRedirectProvider(
            oAuthRedirectUrlProvider = FakeOAuthRedirectUrlProvider(provideResult = ""),
        )

        // The client then sends no redirect at all and the server keeps its own default, which is
        // better than asking to be returned to ":/oidc".
        assertThat(sut.provide()).isNull()
    }
}
