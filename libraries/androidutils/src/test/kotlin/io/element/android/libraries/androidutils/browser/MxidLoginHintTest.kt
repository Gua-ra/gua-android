/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MxidLoginHintTest {
    private val userId = "@alice:example.org"
    private val encodedHint = "org.matrix.msc4198.login_hint=mxid%3A%40alice%3Aexample.org"

    @Test
    fun `a URL without a query gets one`() {
        assertThat("https://auth.example.org/account/".withMxidLoginHint(userId))
            .isEqualTo("https://auth.example.org/account/?$encodedHint")
    }

    @Test
    fun `an existing query keeps its own percent-encoding`() {
        val url = "https://auth.example.org/account/?action=org.matrix.session_view&device_id=AB%2FCD%20EF"
        assertThat(url.withMxidLoginHint(userId))
            .isEqualTo("https://auth.example.org/account/?action=org.matrix.session_view&device_id=AB%2FCD%20EF&$encodedHint")
    }

    @Test
    fun `a trailing separator is not doubled`() {
        assertThat("https://auth.example.org/link?".withMxidLoginHint(userId))
            .isEqualTo("https://auth.example.org/link?$encodedHint")
        assertThat("https://auth.example.org/link?code=ABC&".withMxidLoginHint(userId))
            .isEqualTo("https://auth.example.org/link?code=ABC&$encodedHint")
    }

    @Test
    fun `a fragment stays at the end`() {
        assertThat("https://auth.example.org/account/?a=1#/sessions".withMxidLoginHint(userId))
            .isEqualTo("https://auth.example.org/account/?a=1&$encodedHint#/sessions")
    }

    @Test
    fun `a blank URL is left alone`() {
        assertThat("".withMxidLoginHint(userId)).isEmpty()
    }
}
