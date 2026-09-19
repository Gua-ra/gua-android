/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import io.element.android.libraries.guaresolver.genesis.Base32

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
}
