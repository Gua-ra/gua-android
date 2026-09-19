/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

/**
 * GUA FORK: best-effort E.164 normalization for an address-book number, ported from iOS'
 * `ContactDiscoveryService.normalizeToE164`. International numbers (with `+`, `00`, or the device
 * region's dial code already included) are used as-is; national numbers get the device region's dial
 * code with a single trunk `0` dropped. The identity-service validates and silently skips anything
 * that still isn't valid E.164, so over-normalizing is harmless.
 */
internal object PhoneNumberNormalizer {
    private val E164 = Regex("^\\+[1-9]\\d{6,14}$")

    fun normalize(raw: String, defaultDialCode: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        val defaultDialCodeDigits = defaultDialCode.filter { it.isDigit() }
        if (digits.isEmpty() || defaultDialCodeDigits.isEmpty()) return null

        val cleaned = when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            hasExistingDefaultDialCodePrefix(digits, defaultDialCodeDigits) -> "+$digits"
            else -> {
                var national = digits
                if (national.startsWith("0")) national = national.drop(1)
                "+$defaultDialCodeDigits$national"
            }
        }

        return if (isE164(cleaned)) cleaned else null
    }

    private fun hasExistingDefaultDialCodePrefix(digits: String, defaultDialCode: String): Boolean {
        if (!digits.startsWith(defaultDialCode) || digits.length <= defaultDialCode.length) return false
        if (defaultDialCode == "1") {
            return digits.length == 11
        }
        return isE164("+$digits")
    }

    private fun isE164(number: String): Boolean = E164.matches(number)
}
