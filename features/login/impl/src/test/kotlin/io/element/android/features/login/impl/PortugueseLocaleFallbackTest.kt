/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The app ships values-pt-rBR and no values-pt. `gua_sign_in_with_passkey` has a pt-rBR entry and
 * no values-pt entry in this module, which is the state of every key in the shipped app, so these
 * cases show where a Portuguese device that is not pt-BR reads its strings from.
 */
class PortugueseLocaleFallbackTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "pt-rPT")
    fun `a pt-PT device shows Brazilian Portuguese`() {
        assertThat(passkeyButton()).isEqualTo(BRAZILIAN)
    }

    @Test
    @Config(qualifiers = "pt")
    fun `a pt device without a region shows Brazilian Portuguese`() {
        assertThat(passkeyButton()).isEqualTo(BRAZILIAN)
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `a pt-BR device shows Brazilian Portuguese`() {
        assertThat(passkeyButton()).isEqualTo(BRAZILIAN)
    }

    private fun passkeyButton() = RuntimeEnvironment.getApplication().getString(R.string.gua_sign_in_with_passkey)

    private companion object {
        const val BRAZILIAN = "Entrar com chave de acesso"
    }
}
