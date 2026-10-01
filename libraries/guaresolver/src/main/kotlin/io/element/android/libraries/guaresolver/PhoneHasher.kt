/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import java.security.MessageDigest
import java.util.Locale

/** SHA-256 of the E.164 number under a fixed public domain tag. Privacy hardening, not irreversibility: the phone keyspace is small. */
object PhoneHasher {
    private const val DOMAIN_TAG = "gua-contact-discovery-v1:"

    /** Lowercase hex digest, or null for blank input. Input is normalized to `+` and digits first. */
    fun hash(e164Phone: String): String? {
        val normalized = normalize(e164Phone) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((DOMAIN_TAG + normalized).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hashAll(e164Phones: Collection<String>): List<String> =
        e164Phones.mapNotNull { hash(it) }.distinct()

    private fun normalize(e164Phone: String): String? {
        val trimmed = e164Phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return "+$digits".lowercase(Locale.ROOT)
    }
}
