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

/**
 * GUA FORK: the recovery artifact, both ways (ADM-009 decision 7).
 *
 * The decoder matters as much as the encoder and for a reason that is not symmetry: the artifact never crosses
 * the wire, so material this client accepts and cannot use is spent as a challenge, a step-up and a refusal
 * the user cannot read as "you typed it wrong".
 */
class RecoveryArtifactTest {
    @Test
    fun `an artifact a person typed back is the seed that was shown`() {
        val seed = Ed25519Sign.KeyPair.newKeyPair().privateKey

        val artifact = RecoveryArtifact.encode(seed)

        assertThat(artifact).startsWith(RecoveryArtifact.PREFIX)
        assertThat(RecoveryArtifact.decode(artifact)).isEqualTo(seed)
        // Free-form spacing, because nobody retypes four-character groups exactly as they were printed.
        assertThat(RecoveryArtifact.decode(artifact.replace(" ", "  "))).isEqualTo(seed)
        assertThat(RecoveryArtifact.decode("  $artifact\n")).isEqualTo(seed)
    }

    @Test
    fun `the case it was typed back in is not part of the value`() {
        val seed = Ed25519Sign.KeyPair.newKeyPair().privateKey

        val artifact = RecoveryArtifact.encode(seed)

        // What is rendered is still lowercase and only lowercase. It is the reader that forgives.
        assertThat(artifact).isEqualTo(artifact.lowercase())
        // Off paper it comes back in capitals, and from a keyboard that capitalises, in a mixture. The
        // alphabet is a to z with 2 to 7, so a capital names one alphabet letter and no other and neither
        // spelling can mean a different key. gua-ios has read both since it was written; this is the port
        // agreeing with it rather than handing the owner a key the other phone refuses.
        assertThat(RecoveryArtifact.decode(artifact.uppercase())).isEqualTo(seed)
        assertThat(RecoveryArtifact.decode(artifact.alternatingCase())).isEqualTo(seed)
    }

    @Test
    fun `the fold stops at ASCII, so a look-alike is still refused`() {
        val artifact = RecoveryArtifact.encode(Ed25519Sign.KeyPair.newKeyPair().privateKey)
        // U+212A KELVIN SIGN, which the full Unicode mapping lowercases to `k`. Nobody types it and it is
        // not the case of anything printed, so widening the fold far enough to swallow it would be a second
        // spelling of one key arriving from somewhere nobody typed.
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
        // The alphabet has no look-alike characters, so a character outside it is not a near miss to guess at.
        assertThat(refusalOf { RecoveryArtifact.decode("${RecoveryArtifact.PREFIX} 1111 1111") }?.reason)
            .isEqualTo("bad_artifact")
        // The right alphabet and the wrong length is the typo that would otherwise reach the server.
        assertThat(refusalOf { RecoveryArtifact.decode(artifact.dropLast(4)) }?.reason)
            .isEqualTo("bad_artifact_length")
        // A wrong length is still a wrong length in capitals: forgiving case forgives case and nothing else.
        assertThat(refusalOf { RecoveryArtifact.decode(artifact.dropLast(4).uppercase()) }?.reason)
            .isEqualTo("bad_artifact_length")
    }

    /** Every other character flipped, which is what a keyboard fighting its own autocapitalisation leaves. */
    private fun String.alternatingCase(): String = mapIndexed { index, character ->
        if (index % 2 == 0) character.uppercaseChar() else character
    }.joinToString("")

    private fun refusalOf(block: () -> Unit): InvalidAuthorityRecordException? =
        runCatchingExceptions(block).exceptionOrNull() as? InvalidAuthorityRecordException
}
