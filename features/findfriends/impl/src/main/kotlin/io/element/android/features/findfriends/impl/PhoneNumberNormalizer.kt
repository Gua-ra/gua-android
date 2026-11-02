/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

/**
 * GUA FORK: best-effort E.164 normalization for an address-book number, kept in step with iOS'
 * `ContactDiscoveryService.normalizeToE164`. International numbers (with `+`, `00`, or the device
 * region's dial code already included) are used as-is; national numbers get the device region's dial
 * code with a single trunk `0` dropped. Brazilian numbers are reduced to the canonical form accounts
 * are registered with. The identity-service validates and silently skips anything that still isn't
 * valid E.164.
 */
internal object PhoneNumberNormalizer {
    private val E164 = Regex("^\\+[1-9]\\d{6,14}$")
    private const val BRAZIL = "55"

    fun normalize(raw: String, defaultDialCode: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        val defaultDialCodeDigits = defaultDialCode.filter { it.isDigit() }
        if (digits.isEmpty() || defaultDialCodeDigits.isEmpty()) return null

        val international = when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            hasExistingDefaultDialCodePrefix(digits, defaultDialCodeDigits) -> digits
            else -> null
        }
        val cleaned = when {
            international == null && defaultDialCodeDigits == BRAZIL ->
                brazilNational(digits)?.let { "+$BRAZIL$it" }
            international == null -> "+$defaultDialCodeDigits${digits.removePrefix("0")}"
            international.startsWith(BRAZIL) ->
                brazilNational(international.drop(BRAZIL.length))?.let { "+$BRAZIL$it" }
            else -> "+$international"
        }

        return cleaned?.takeIf { isE164(it) }
    }

    private fun hasExistingDefaultDialCodePrefix(digits: String, defaultDialCode: String): Boolean {
        if (!digits.startsWith(defaultDialCode) || digits.length <= defaultDialCode.length) return false
        return when (defaultDialCode) {
            "1" -> digits.length == 11
            // A national number is at most 11 digits, so this also keeps area code 55 national.
            BRAZIL -> digits.length == 12 || digits.length == 13
            else -> isE164("+$digits")
        }
    }

    /**
     * Returns the area code plus subscriber number, or null when [national] cannot be a Brazilian number.
     * Drops the trunk `0` and the two-digit carrier selection code that may follow it, and adds the
     * leading 9 that every mobile number gained in the 2012 to 2016 migration.
     */
    private fun brazilNational(national: String): String? {
        var number = national
        if (number.startsWith("0")) {
            number = number.drop(1)
            if (number.length == 12 || number.length == 13) number = number.drop(2)
        }
        if (number.length < 10 || number[0] == '0' || number[1] == '0') return null
        if (number.length == 10 && number[2] in '6'..'9') number = number.take(2) + "9" + number.drop(2)
        return when (number.length) {
            10 -> number
            11 -> number.takeIf { it[2] == '9' }
            else -> null
        }
    }

    private fun isE164(number: String): Boolean = E164.matches(number)
}
