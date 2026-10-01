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

class RecoveryArtifactTest {
    @Test
    fun `an artifact a person typed back is the seed that was shown`() {
        val seed = Ed25519Sign.KeyPair.newKeyPair().privateKey

        val artifact = RecoveryArtifact.encode(seed)

        assertThat(artifact).startsWith(RecoveryArtifact.PREFIX)
        assertThat(RecoveryArtifact.decode(artifact)).isEqualTo(seed)
        assertThat(RecoveryArtifact.decode(artifact.replace(" ", "  "))).isEqualTo(seed)
        assertThat(RecoveryArtifact.decode("  $artifact\n")).isEqualTo(seed)
    }

    @Test
    fun `the case it was typed back in is not part of the value`() {
        val seed = Ed25519Sign.KeyPair.newKeyPair().privateKey

        val artifact = RecoveryArtifact.encode(seed)

        assertThat(artifact).isEqualTo(artifact.lowercase())
        assertThat(RecoveryArtifact.decode(artifact.uppercase())).isEqualTo(seed)
        assertThat(RecoveryArtifact.decode(artifact.alternatingCase())).isEqualTo(seed)
    }

    @Test
    fun `the fold stops at ASCII, so a look-alike is still refused`() {
        val artifact = RecoveryArtifact.encode(Ed25519Sign.KeyPair.newKeyPair().privateKey)
        val withKelvin = RecoveryArtifact.PREFIX + " " + artifact.removePrefix(RecoveryArtifact.PREFIX + " ")
            .replaceFirst(artifact.last(), '\u212A')

        assertThat(refusalOf { RecoveryArtifact.decode("${RecoveryArtifact.PREFIX} ye\u212Ao") }).isNotNull()
        assertThat(refusalOf { RecoveryArtifact.decode(withKelvin) }).isNotNull()
    }

    @Test
    fun `malformed material is refused here, naming which part is wrong`() {
        val artifact = RecoveryArtifact.encode(Ed25519Sign.KeyPair.newKeyPair().privateKey)

        assertThat(refusalOf { RecoveryArtifact.decode("please let me in") }?.reason)
            .isEqualTo("bad_artifact_prefix")
        assertThat(refusalOf { RecoveryArtifact.decode(RecoveryArtifact.PREFIX) }?.reason)
            .isEqualTo("bad_artifact")
        assertThat(refusalOf { RecoveryArtifact.decode("${RecoveryArtifact.PREFIX} 1111 1111") }?.reason)
            .isEqualTo("bad_artifact")
        assertThat(refusalOf { RecoveryArtifact.decode(artifact.dropLast(4)) }?.reason)
            .isEqualTo("bad_artifact_length")
        assertThat(refusalOf { RecoveryArtifact.decode(artifact.dropLast(4).uppercase()) }?.reason)
            .isEqualTo("bad_artifact_length")
    }

    private fun String.alternatingCase(): String = mapIndexed { index, character ->
        if (index % 2 == 0) character.uppercaseChar() else character
    }.joinToString("")

    private fun refusalOf(block: () -> Unit): InvalidAuthorityRecordException? =
        runCatchingExceptions(block).exceptionOrNull() as? InvalidAuthorityRecordException
}
