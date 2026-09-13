/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EphemeralCustomTabsTest {
    @Test
    fun `a default provider that supports ephemeral browsing is used`() {
        val checked = mutableListOf<String>()
        val provider = chooseEphemeralProvider(
            defaultProvider = "com.android.chrome",
            supportsEphemeralBrowsing = {
                checked += it
                true
            },
        )
        assertThat(provider).isEqualTo("com.android.chrome")
        assertThat(checked).containsExactly("com.android.chrome")
    }

    @Test
    fun `a provider without ephemeral browsing falls back to the shared tab`() {
        val provider = chooseEphemeralProvider(
            defaultProvider = "org.example.browser",
            supportsEphemeralBrowsing = { false },
        )
        assertThat(provider).isNull()
    }

    @Test
    fun `no Custom Tabs provider falls back without asking about support`() {
        val provider = chooseEphemeralProvider(
            defaultProvider = null,
            supportsEphemeralBrowsing = { error("must not be asked") },
        )
        assertThat(provider).isNull()
        assertThat(chooseEphemeralProvider(defaultProvider = "", supportsEphemeralBrowsing = { true })).isNull()
    }

    @Test
    fun `a failing support check counts as unsupported`() {
        val provider = chooseEphemeralProvider(
            defaultProvider = "com.android.chrome",
            supportsEphemeralBrowsing = { throw SecurityException("package not visible") },
        )
        assertThat(provider).isNull()
    }
}
