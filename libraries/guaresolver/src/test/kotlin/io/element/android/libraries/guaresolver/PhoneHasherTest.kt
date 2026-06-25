/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneHasherTest {
    @Test
    fun `hash is a stable 64-char hex digest`() {
        val hash = PhoneHasher.hash("+15551234567")
        assertThat(hash).isNotNull()
        assertThat(hash).hasLength(64)
        assertThat(hash).matches("[0-9a-f]{64}")
    }

    @Test
    fun `hash never echoes the raw number`() {
        val hash = PhoneHasher.hash("+15551234567")
        assertThat(hash).doesNotContain("5551234567")
        assertThat(hash).doesNotContain("+")
    }

    @Test
    fun `formatting differences normalize to the same digest`() {
        assertThat(PhoneHasher.hash("+1 (555) 123-4567")).isEqualTo(PhoneHasher.hash("+15551234567"))
    }

    @Test
    fun `blank input hashes to null`() {
        assertThat(PhoneHasher.hash("")).isNull()
        assertThat(PhoneHasher.hash("   ")).isNull()
    }

    @Test
    fun `hashAll drops blanks and de-duplicates`() {
        val hashes = PhoneHasher.hashAll(listOf("+15551234567", "+1 555 123 4567", "", "+5511999998888"))
        assertThat(hashes).hasSize(2)
    }
}
