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
 * GUA FORK: client-side phone-number protection for contact discovery.
 *
 * Mirrors iOS' privacy posture (numbers are digested rather than handed over in the clear) and
 * makes it explicit on Android: the [IdentityServiceClient] only ever sees a stable SHA-256 digest
 * of each E.164 number, prefixed with a fixed, public domain-separation tag so the digest can only
 * collide with the identity-service's own contact-discovery table (and not, say, a leaked rainbow
 * table built for a different purpose). The raw address book is never persisted and the raw numbers
 * never leave the device.
 *
 * The tag is intentionally NOT a secret — both the client and the identity-service must derive the
 * same digest for a number, so a per-device random salt is impossible here. Phone numbers are a
 * small keyspace, so this is a privacy-hardening measure (defence in depth + no plaintext at rest
 * on the server side), not a guarantee of irreversibility.
 */
object PhoneHasher {
    /** Public, fixed domain-separation tag agreed with the identity-service contact-discovery table. */
    private const val DOMAIN_TAG = "gua-contact-discovery-v1:"

    /**
     * Protect a single E.164 phone number. Input is normalized to its leading `+` and digits before
     * hashing so trivial formatting differences map to the same digest. Returns a lowercase hex
     * SHA-256 digest, or `null` for blank input.
     */
    fun hash(e164Phone: String): String? {
        val normalized = normalize(e164Phone) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((DOMAIN_TAG + normalized).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Protect a batch of E.164 numbers, dropping blanks and de-duplicating so the lookup payload is
     * minimal. Order is not significant.
     */
    fun hashAll(e164Phones: Collection<String>): List<String> =
        e164Phones.mapNotNull { hash(it) }.distinct()

    private fun normalize(e164Phone: String): String? {
        val trimmed = e164Phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return "+$digits".lowercase(Locale.ROOT)
    }
}
