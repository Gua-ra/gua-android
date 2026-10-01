/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Ed25519Sign
import io.element.android.libraries.core.extensions.runCatchingExceptions
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Every case is asserted against this implementation and against the JDK Ed25519 provider, the decoder the server runs. */
class Ed25519PublicKeysTest {
    @Test
    fun `x is zero with the sign bit set is refused, exactly as the server refuses it`() {
        for (encoded in listOf(Y_IS_ONE_SIGN_SET, Y_IS_P_MINUS_ONE_SIGN_SET)) {
            val raw = encoded.hexToBytes()
            assertThat(Ed25519PublicKeys.isOnCurve(raw)).isFalse()
            assertThat(decodesWithTheJdk(raw)).isFalse()
        }
    }

    @Test
    fun `the same two y values decode when the sign bit is clear`() {
        for (encoded in listOf(Y_IS_ONE, Y_IS_P_MINUS_ONE)) {
            val raw = encoded.hexToBytes()
            assertThat(Ed25519PublicKeys.isOnCurve(raw)).isTrue()
            assertThat(decodesWithTheJdk(raw)).isTrue()
        }
    }

    @Test
    fun `real keys still decode on both sides, sign bit set or not`() {
        repeat(REAL_KEY_SAMPLES) {
            val raw = Ed25519Sign.KeyPair.newKeyPair().publicKey
            assertThat(Ed25519PublicKeys.isOnCurve(raw)).isTrue()
            assertThat(decodesWithTheJdk(raw)).isTrue()
        }
    }

    @Test
    fun `a y at or above the field prime is still refused`() {
        val raw = Y_IS_THE_FIELD_PRIME.hexToBytes()

        assertThat(Ed25519PublicKeys.isOnCurve(raw)).isFalse()
        assertThat(decodesWithTheJdk(raw)).isFalse()
    }

    private fun decodesWithTheJdk(rawPublicKey: ByteArray): Boolean = runCatchingExceptions {
        val spki = SPKI_PREFIX.hexToBytes() + rawPublicKey
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        Signature.getInstance("Ed25519").initVerify(publicKey)
    }.isSuccess

    private companion object {
        private const val SPKI_PREFIX = "302a300506032b6570032100"
        private const val REAL_KEY_SAMPLES = 32

        /** y = 1, the identity, with the sign bit asking for a negative x that does not exist. */
        private const val Y_IS_ONE_SIGN_SET = "0100000000000000000000000000000000000000000000000000000000000080"

        /** y = p - 1, the order 2 point, same rule. */
        private const val Y_IS_P_MINUS_ONE_SIGN_SET = "ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

        private const val Y_IS_ONE = "0100000000000000000000000000000000000000000000000000000000000000"
        private const val Y_IS_P_MINUS_ONE = "ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"

        /** y = p, which no encoding may name. */
        private const val Y_IS_THE_FIELD_PRIME = "edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"

        private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
