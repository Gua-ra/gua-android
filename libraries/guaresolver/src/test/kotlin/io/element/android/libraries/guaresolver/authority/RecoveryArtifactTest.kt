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
        // Case is part of the value: the decoder is case-sensitive and the artifact is shown in lowercase.
        assertThat(refusalOf { RecoveryArtifact.decode(artifact.uppercase()) }).isNotNull()
    }

    private fun refusalOf(block: () -> Unit): InvalidAuthorityRecordException? =
        runCatchingExceptions(block).exceptionOrNull() as? InvalidAuthorityRecordException
}
