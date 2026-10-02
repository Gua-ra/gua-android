/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import java.security.MessageDigest
import java.util.Locale

/**
 * Client-side phone-number protection for contact discovery: the [IdentityServiceClient] only ever
 * sees a SHA-256 digest of each E.164 number, prefixed with a fixed public domain-separation tag.
 *
 * The tag is not a secret: client and identity service must derive the same digest, so a per-device
 * salt is impossible. Phone numbers are a small keyspace, so this is privacy hardening, not a
 * guarantee of irreversibility.
 */
object PhoneHasher {
    /** Public, fixed domain-separation tag agreed with the identity-service contact-discovery table. */
    private const val DOMAIN_TAG = "gua-contact-discovery-v1:"

    /** Lowercase hex digest, or null for blank input. Input is normalized to `+` and digits first. */
    fun hash(e164Phone: String): String? {
        val normalized = normalize(e164Phone) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((DOMAIN_TAG + normalized).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Hashes a batch, dropping blanks and de-duplicating. Order is not significant. */
    fun hashAll(e164Phones: Collection<String>): List<String> =
        e164Phones.mapNotNull { hash(it) }.distinct()

    private fun normalize(e164Phone: String): String? {
        val trimmed = e164Phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return "+$digits".lowercase(Locale.ROOT)
    }
}
