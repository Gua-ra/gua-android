/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.guaresolver.GuaResolverConfig
import org.junit.Test

class ResolverTrustRootsTest {
    @Test
    fun `the committed production roots list the authority key as both roots`() {
        val roots = ResolverTrustRoots.parse(GuaResolverConfig.PROD_RESOLVER_TRUST_ROOTS)

        assertThat(roots.authorityKeys.map { it.id }).containsExactly("gua-authority-prod")
        assertThat(roots.policyKeys.map { it.id }).containsExactly("gua-authority-prod")
        assertThat(roots.authorityKeys.single().rawPublicKey).isEqualTo(roots.policyKeys.single().rawPublicKey)
        assertThat(roots.authorityThreshold).isEqualTo(1)
        assertThat(roots.policyThreshold).isEqualTo(1)
    }

    @Test
    fun `thresholds and several keys per role are read`() {
        val roots = ResolverTrustRoots.parse(
            "authority:a:$A_KEY\nauthority:b:$ANOTHER_KEY  authority-threshold:2 policy-threshold:1 policy:g:$ANOTHER_KEY"
        )

        assertThat(roots.authorityKeys.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(roots.authorityThreshold).isEqualTo(2)
        assertThat(roots.policyKeys.map { it.id }).containsExactly("g")
        assertThat(roots.policyThreshold).isEqualTo(1)
    }

    @Test
    fun `an empty value configures no keys`() {
        val roots = ResolverTrustRoots.parse("  ")

        assertThat(roots.authorityKeys).isEmpty()
        assertThat(roots.policyKeys).isEmpty()
    }

    @Test
    fun `a malformed value is refused rather than partly read`() {
        val malformed = listOf(
            "authority:a",
            "authority:a:not-base64",
            "authority::$A_KEY",
            "trusted:a:$A_KEY",
            "authority-threshold:0",
            "authority:a:$NOT_A_POINT",
        )
        for (value in malformed) {
            assertThat(runCatchingExceptions { ResolverTrustRoots.parse(value) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    private companion object {
        private const val A_KEY = "MCowBQYDK2VwAyEA11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo="
        private const val ANOTHER_KEY = "MCowBQYDK2VwAyEAPUAXw+hDiVqStwqnTRt+vJyYLM8uxJaMwM1V8Sr0Zgw="

        /** The SPKI prefix around 32 zero bytes, which decodes but is not a key anyone may trust. */
        private const val NOT_A_POINT = "MCowBQYDK2VwAyEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}
