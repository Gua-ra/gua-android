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
 * The encoding is the base32 alphabet the accountId already uses, lowercase with no padding, in groups of
 * four. It is rendered in lowercase and only ever in lowercase; what forgives case is the reader, because
 * the artifact comes back off paper and off another phone's screen.
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
     * reproduce the spacing. So is case, by [foldAsciiCase]: the alphabet is a to z with 2 to 7, so folding an
     * ASCII capital onto its lowercase names that letter and no other, and reading either case therefore
     * admits no byte string a lowercase spelling could not already name. Refusing the uppercase transcription
     * instead would fail exactly the replaced-phone case this artifact exists for, on a key that is written on
     * paper and read back once, and gua-ios has forgiven case since it was written.
     *
     * The accountId decoder keeps its own strictness and is not touched by this: that one is a permanent
     * identifier a signature covers, so ADM-001 L4 gives it exactly one spelling. An artifact is neither.
     */
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

    /**
     * Lowercases A to Z and nothing else.
     *
     * Not `lowercase()`: that applies the full Unicode mapping, which folds characters no keyboard on this
     * path produces onto alphabet letters, the Kelvin sign onto `k` among them. Widening the door past the
     * case of the printed alphabet would be a second spelling of the same key arriving from somewhere nobody
     * typed, and the golden vectors pin that refusal rather than leaving it to the two ports to agree by
     * accident. Locale is not a factor here either, which `lowercase()` would also have to be argued about.
     */
    private fun foldAsciiCase(value: String): String = buildString(value.length) {
        for (character in value) {
            // The range is the whole of the rule. Inside it `lowercaseChar` is ASCII and nothing else.
            append(if (character in 'A'..'Z') character.lowercaseChar() else character)
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
