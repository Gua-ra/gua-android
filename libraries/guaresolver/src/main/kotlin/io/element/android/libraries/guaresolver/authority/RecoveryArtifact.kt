/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import io.element.android.libraries.guaresolver.genesis.Base32
import io.element.android.libraries.guaresolver.genesis.InvalidGenesisException

/**
 * GUA FORK: the recovery artifact ADM-009 decision 7 requires the user to take, rendered so a person can
 * write it on paper and type it back.
 *
 * It is the PRIVATE recovery authority key, which is the only thing that can authorize an
 * `AuthorityRecovery` at rank 2. That is the whole point of showing it: an account that loses every device
 * and this artifact never regains authority, and unrecoverable is a permitted end state. Whoever holds it,
 * together with a way into the account, can take the account after a wait, so the copy beside it says so.
 *
 * The encoding is the base32 alphabet the accountId already uses, lowercase with no padding and no
 * look-alike characters, in groups of four. It is never uppercased: the decoder is case-sensitive, and an
 * artifact a person copied in the case it was shown in has to be the artifact that comes back.
 */
object RecoveryArtifact {
    /** Names the framework and the version, so a future framework 0x02 artifact is not mistaken for this one. */
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

    /**
     * The 32 seed bytes an artifact a person typed back carries, or a refusal naming what is wrong with it.
     *
     * Validated HERE rather than at the server, because the server never sees it: the artifact is a private
     * key and what crosses the wire is a signature by it. Malformed material therefore has to fail on this
     * side or it fails as `authority_signer_refused` after a challenge and a step-up have already been spent,
     * which is a wrong answer to "I typed it wrong".
     *
     * Whitespace between groups is free-form, because a person retyping four-character groups will not
     * reproduce the spacing. Case is not: the alphabet is lowercase and the decoder is case-sensitive, so an
     * artifact copied in the case it was shown in is the artifact that comes back.
     */
    fun decode(artifact: String): ByteArray {
        val collapsed = artifact.trim().split(WHITESPACE).filter { it.isNotEmpty() }
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
            // Re-raised without the value: what failed to decode is key material.
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

    private val WHITESPACE = Regex("\\s+")
}
