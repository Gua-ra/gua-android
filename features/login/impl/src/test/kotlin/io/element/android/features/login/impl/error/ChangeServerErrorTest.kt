/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.error

import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.impl.login.PasskeySignInError
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.ui.strings.CommonStrings
import org.junit.Test
import java.io.IOException

class ChangeServerErrorTest {
    @Test
    fun `a resolver that cannot be reached shows the translated connection message`() {
        assertThat(ChangeServerError.from(ResolverError.Transport(IOException("Could not reach the routing service."))))
            .isEqualTo(ChangeServerError.Error(messageId = CommonStrings.error_network_or_server_issue))
        assertThat(ChangeServerError.from(ResolverError.Server(503)))
            .isEqualTo(ChangeServerError.Error(messageId = CommonStrings.error_network_or_server_issue))
    }

    @Test
    fun `other errors show the translated generic message, never their own text`() {
        listOf(
            ResolverError.NotConfigured,
            ResolverError.MalformedResponse,
            PasskeySignInError.NotConfigured,
            AuthenticationException.Generic("An English message from the SDK"),
            AuthenticationException.OAuth("An English message from the SDK"),
            AuthenticationException.AccountAlreadyLoggedIn("@alice:server.org"),
            IllegalStateException("An English message"),
        ).forEach { error ->
            assertThat(ChangeServerError.from(error)).isEqualTo(ChangeServerError.Error(messageId = CommonStrings.error_unknown))
        }
    }
}
