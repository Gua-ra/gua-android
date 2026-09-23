/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import java.security.MessageDigest

/**
 * GUA FORK: the short human fingerprint of a device authority key (ADM-009 decision 5, revision 4). Kotlin
 * side of the identity-service `AuthorityFingerprint`.
 *
 * It is the only thing crossing between two phones that a person has to compare, so both phones compute it
 * from the same 32 public bytes rather than being told it by a server: a server-issued nonce would prove only
 * that both had spoken to the same server, which is the property already assumed and not the one being
 * checked. This client therefore computes it as well as reading it, and refuses to show one it did not
 * recompute, because a fingerprint the granting phone took on trust is a fingerprint an attacker can choose.
 *
 * Eight characters from the 31-character alphabet {@code ABCDEFGHJKLMNPQRSTUVWXYZ2346789}: the same alphabet
 * as the browser-approval code, with I, O, 0, 1, 5 and S left out, because a fingerprint two people read
 * aloud across a room fails at exactly the characters that sound or look alike.
 *
 * Eight characters carry just under 40 bits. That is not collision resistance and is not meant to be: it
 * defends a human comparison inside a live ceremony against a key swapped in the middle, and the server
 * separately refuses a grant over any key that is not a live candidate of that same account, so an attacker
 * has to find a near-collision against one specific key inside the candidate's ten minutes.
 */
object AuthorityFingerprint {
    /** No I, O, 0, 1, 5 or S. A fingerprint gets read aloud. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ2346789"

    /** Eight characters, shown in two groups of four. */
    const val LENGTH = 8

    private const val DOMAIN = "gua-authority-candidate.v1"

    private const val GROUP_SIZE = 4

    /**
     * The fingerprint of one raw Ed25519 device key.
     *
     * Domain-separated, and taken from the front of the digest rather than from the key itself: a fingerprint
     * that showed key bytes would make two keys with a shared prefix look identical.
     */
    fun of(rawDeviceKey: ByteArray): String {
        if (rawDeviceKey.size != AuthorityRecord.KEY_LENGTH) {
            throw InvalidAuthorityRecordException(
                "wrong_length",
                "a device key is ${AuthorityRecord.KEY_LENGTH} bytes",
            )
        }
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(DOMAIN.toByteArray(Charsets.US_ASCII))
            update(rawDeviceKey)
        }.digest()
        return buildString(LENGTH) {
            for (index in 0 until LENGTH) {
                append(ALPHABET[(digest[index].toInt() and 0xFF) % ALPHABET.length])
            }
        }
    }

    /** The same eight characters in two groups, which is how a person reads them out. */
    fun grouped(fingerprint: String): String = fingerprint.chunked(GROUP_SIZE).joinToString(" ")
}
