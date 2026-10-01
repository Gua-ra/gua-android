/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import java.security.MessageDigest

object AuthorityFingerprint {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ2346789"

    const val LENGTH = 8

    private const val DOMAIN = "gua-authority-candidate.v1"

    private const val GROUP_SIZE = 4

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

    fun grouped(fingerprint: String): String = fingerprint.chunked(GROUP_SIZE).joinToString(" ")
}
