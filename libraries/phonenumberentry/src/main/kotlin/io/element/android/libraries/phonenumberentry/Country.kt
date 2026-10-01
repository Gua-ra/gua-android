/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

import android.content.Context
import android.os.Parcelable
import android.telephony.TelephonyManager
import kotlinx.parcelize.Parcelize
import java.util.Locale

@Parcelize
data class Country(
    val isoCode: String,
    val dialCode: String,
) : Parcelable {
    val name: String
        get() = Locale("", isoCode).getDisplayCountry(Locale.getDefault()).ifEmpty { isoCode }

    val flag: String
        get() {
            val base = 0x1F1E6 - 0x41 // Regional Indicator Symbol "A" - ASCII "A"
            return buildString {
                for (char in isoCode.uppercase()) {
                    appendCodePoint(base + char.code)
                }
            }
        }

    val nationalExample: String
        get() = nationalExamples[isoCode] ?: "123 456 7890"

    /** Null when no curated example exists. */
    val nationalDigitLength: Int?
        get() {
            val example = nationalExamples[isoCode] ?: return null
            val count = example.count { it.isDigit() }
            return if (count > 0) count else null
        }

    /** Extra digits past the mask are appended unformatted. */
    fun formatNational(rawDigits: String): String {
        val digits = rawDigits.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        val mask = nationalMasks[isoCode] ?: deriveMask(nationalExample)
        val result = StringBuilder()
        var digitIndex = 0
        for (ch in mask) {
            if (digitIndex == digits.length) break
            if (ch == '#') {
                result.append(digits[digitIndex])
                digitIndex++
            } else {
                result.append(ch)
            }
        }
        if (digitIndex < digits.length) {
            result.append(digits.substring(digitIndex))
        }
        return result.toString()
    }

    companion object {
        val fallback = Country(isoCode = "US", dialCode = "1")

        /** Prefer [deviceDefault]: the locale is a language preference, not a location. */
        val deviceDefault: Country
            get() = fromRegion(Locale.getDefault().country)

        /** SIM first, then the network, then the locale. */
        fun deviceDefault(context: Context?): Country {
            val telephony = context?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val region = telephony?.simCountryIso?.takeIf { it.isNotBlank() }
                ?: telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
                ?: Locale.getDefault().country
            return fromRegion(region)
        }

        private fun fromRegion(region: String): Country {
            val normalised = region.uppercase(Locale.ROOT)
            return all.firstOrNull { it.isoCode == normalised } ?: fallback
        }

        fun find(isoCode: String): Country? = all.firstOrNull { it.isoCode == isoCode.uppercase() }

        /** Longest-prefix dial-code match. Some dial codes are 4 digits. */
        fun parse(initialPhoneNumber: String, context: Context? = null): Pair<Country, String> =
            parse(initialPhoneNumber, deviceDefault(context))

        fun parse(initialPhoneNumber: String, default: Country): Pair<Country, String> {
            val trimmed = initialPhoneNumber.trim()
            if (!trimmed.startsWith("+")) return default to ""
            val digits = trimmed.drop(1).filter { it.isDigit() }
            for (length in minOf(4, digits.length) downTo 1) {
                val prefix = digits.take(length)
                val country = all.firstOrNull { it.dialCode == prefix }
                if (country != null) {
                    return country to digits.drop(length)
                }
            }
            return default to digits
        }

        /** Longest-prefix dial-code match, then the Canadian area codes for +1. Null when the current selection is already the best match. */
        fun detect(localDigits: String, current: Country): Country? {
            val combined = current.dialCode + localDigits

            val maxLen = minOf(5, combined.length)
            if (maxLen >= 2) {
                for (length in maxLen downTo 2) {
                    val prefix = combined.take(length)
                    if (prefix == current.dialCode) continue
                    val match = all.firstOrNull { it.dialCode == prefix }
                    if (match != null && match != current) {
                        return match
                    }
                }
            }

            if (current.dialCode == "1" && localDigits.length >= 3) {
                val area = localDigits.take(3)
                val isCanadian = canadianAreaCodes.contains(area)
                if (isCanadian && current.isoCode != "CA") return find("CA")
                if (!isCanadian && current.isoCode == "CA") return find("US")
            }

            return null
        }

        /** Strips a redundant country code only when unambiguous. Runs on every text change, so it must be a no-op for ordinary local typing. */
        fun normalize(rawInput: String, current: Country): Pair<Country, String> {
            val trimmed = rawInput.trim()

            if (trimmed.startsWith("+")) {
                val digits = trimmed.filter { it.isDigit() }
                for (length in minOf(4, digits.length) downTo 1) {
                    val prefix = digits.take(length)
                    val country = all.firstOrNull { it.dialCode == prefix }
                    if (country != null) {
                        return country to digits.drop(length)
                    }
                }
                return current to digits
            }

            val digits = trimmed.filter { it.isDigit() }

            val dial = current.dialCode
            val nationalLength = current.nationalDigitLength
            if (digits.length > dial.length && digits.startsWith(dial) && nationalLength != null) {
                val remainder = digits.drop(dial.length)
                // Only when the remainder is exactly a full national number and does not itself start with the dial code.
                if (remainder.length == nationalLength && !remainder.startsWith(dial)) {
                    return current to remainder
                }
            }

            return current to digits
        }

        private fun deriveMask(example: String): String =
            example.map { if (it.isDigit()) '#' else it }.joinToString("")
    }
}
