/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Ed25519Sign
import io.element.android.libraries.core.extensions.runCatchingExceptions
import org.junit.Test

class AuthorityFingerprintTest {
    @Test
    fun `the RFC 8032 test keys produce the same eight characters as the server`() {
        assertThat(AuthorityFingerprint.of(hex(TEST1_PUBLIC_KEY))).isEqualTo("9KDCZT8A")
        assertThat(AuthorityFingerprint.of(hex(TEST2_PUBLIC_KEY))).isEqualTo("8N2EM3D8")
        assertThat(AuthorityFingerprint.of(hex(TEST3_PUBLIC_KEY))).isEqualTo("VLP7JJES")
    }

    @Test
    fun `every character comes from an alphabet with no look-alikes`() {
        repeat(20) {
            val fingerprint = AuthorityFingerprint.of(Ed25519Sign.KeyPair.newKeyPair().publicKey)

            assertThat(fingerprint).hasLength(AuthorityFingerprint.LENGTH)
            assertThat(fingerprint.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ2346789" }).isTrue()
        }
    }

    @Test
    fun `two keys that share a prefix do not look alike`() {
        val first = ByteArray(32) { if (it == 31) 1 else 0 }
        val second = ByteArray(32) { if (it == 31) 2 else 0 }

        assertThat(AuthorityFingerprint.of(first)).isNotEqualTo(AuthorityFingerprint.of(second))
    }

    @Test
    fun `something that is not a key has no fingerprint at all`() {
        val refusal = runCatchingExceptions { AuthorityFingerprint.of(ByteArray(16)) }
            .exceptionOrNull() as? InvalidAuthorityRecordException

        assertThat(refusal?.reason).isEqualTo("wrong_length")
    }

    @Test
    fun `it is shown in two groups, which is how a person reads it out`() {
        assertThat(AuthorityFingerprint.grouped("9KDCZT8A")).isEqualTo("9KDC ZT8A")
    }

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

    private companion object {
        private const val TEST1_PUBLIC_KEY =
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        private const val TEST2_PUBLIC_KEY =
            "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"
        private const val TEST3_PUBLIC_KEY =
            "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025"
    }
}
