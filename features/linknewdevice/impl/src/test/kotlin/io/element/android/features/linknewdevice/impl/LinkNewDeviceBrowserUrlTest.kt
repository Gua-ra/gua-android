/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.test.FakeSessionEnterpriseService
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LinkNewDeviceBrowserUrlTest {
    @Test
    fun `the approval page is bound to this account after the enterprise tweak`() = runTest {
        val service = FakeSessionEnterpriseService(
            tweakMasUrlResult = { "$it&tweaked=1" },
        )
        val url = service.linkNewDeviceBrowserUrl(
            verificationUri = "https://auth.example.org/link?code=a%2Fb%20c",
            sessionId = SessionId("@alice:example.org"),
        )
        assertThat(url).isEqualTo(
            "https://auth.example.org/link?code=a%2Fb%20c&tweaked=1" +
                "&org.matrix.msc4198.login_hint=mxid%3A%40alice%3Aexample.org"
        )
    }

    @Test
    fun `a verification address without a query gets the hint as its query`() = runTest {
        val service = FakeSessionEnterpriseService(
            tweakMasUrlResult = { it },
        )
        val url = service.linkNewDeviceBrowserUrl(
            verificationUri = "https://auth.example.org/link",
            sessionId = SessionId("@alice:example.org"),
        )
        assertThat(url).isEqualTo("https://auth.example.org/link?org.matrix.msc4198.login_hint=mxid%3A%40alice%3Aexample.org")
    }
}
