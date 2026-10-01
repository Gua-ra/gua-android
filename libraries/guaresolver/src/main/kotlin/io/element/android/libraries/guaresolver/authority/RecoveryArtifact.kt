/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import io.element.android.libraries.guaresolver.genesis.Base32
import io.element.android.libraries.guaresolver.genesis.InvalidGenesisException

object RecoveryArtifact {
    const val PREFIX = "gua-recovery-1"

    private const val GROUP_SIZE = 4

    fun encode(recoverySeed: ByteArray): String {
        if (recoverySeed.size != AuthorityRecord.KEY_LENGTH) {
            throw InvalidAuthorityRecordException(
                "wrong_length",
                "a recovery authority seed is ${AuthorityRecord.KEY_LENGTH} bytes",
            )
        }
        val groups = Base32.encode(recoverySeed).chunked(GROUP_SIZE)
        return "$PREFIX " + groups.joinToString(" ")
    }

    fun decode(artifact: String): ByteArray {
        val collapsed = foldAsciiCase(artifact.trim()).split(WHITESPACE).filter { it.isNotEmpty() }
        if (collapsed.isEmpty() || collapsed.first() != PREFIX) {
            throw InvalidAuthorityRecordException(
                "bad_artifact_prefix",
                "a recovery key starts with $PREFIX",
            )
        }
        val body = collapsed.drop(1).joinToString("")
        if (body.isEmpty()) {
            throw InvalidAuthorityRecordException("bad_artifact", "a recovery key carries its own value")
        }
        val seed = try {
            Base32.decode(body)
        } catch (_: InvalidGenesisException) {
            // Re-raised without the value, which is key material.
            throw InvalidAuthorityRecordException("bad_artifact", "that is not a recovery key from this app")
        }
        if (seed.size != AuthorityRecord.KEY_LENGTH) {
            throw InvalidAuthorityRecordException(
                "bad_artifact_length",
                "a recovery key is ${AuthorityRecord.KEY_LENGTH} bytes",
            )
        }
        return seed
    }

    /** ASCII only: `lowercase()` would also fold characters such as the Kelvin sign onto alphabet letters. */
    private fun foldAsciiCase(value: String): String = buildString(value.length) {
        for (character in value) {
            append(if (character in 'A'..'Z') character.lowercaseChar() else character)
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
