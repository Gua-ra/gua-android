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

/**
 * GUA FORK: a country/region for phone entry — its ISO 3166-1 alpha-2 code plus its E.164 dial code.
 * Android counterpart of iOS `Country`. Provides the flag emoji, a localized name, a national-format
 * example used as the input placeholder, and live national-format masking.
 */
@Parcelize
data class Country(
    val isoCode: String,
    val dialCode: String,
) : Parcelable {
    /** Localized region name from the device locale, falling back to the ISO code. */
    val name: String
        get() = Locale("", isoCode).getDisplayCountry(Locale.getDefault()).ifEmpty { isoCode }

    /** Flag emoji built from regional-indicator scalars (e.g. "US" -> two regional-indicator symbols). */
    val flag: String
        get() {
            val base = 0x1F1E6 - 0x41 // Regional Indicator Symbol "A" - ASCII "A"
            return buildString {
                for (char in isoCode.uppercase()) {
                    appendCodePoint(base + char.code)
                }
            }
        }

    /**
     * National-format example mobile number for the country, used as the input placeholder
     * (e.g. US "555 123 4567", BR "11 91234 5678"). Falls back to a generic 10-digit hint.
     */
    val nationalExample: String
        get() = nationalExamples[isoCode] ?: "123 456 7890"

    /**
     * Number of digits in a national-format subscriber number for this country, inferred from its
     * [nationalExamples] entry (e.g. US "555 123 4567" -> 10, BR "11 91234 5678" -> 11). Returns
     * `null` when no curated example exists, so callers can stay conservative about stripping.
     */
    val nationalDigitLength: Int?
        get() {
            val example = nationalExamples[isoCode] ?: return null
            val count = example.count { it.isDigit() }
            return if (count > 0) count else null
        }

    /**
     * Formats the user-typed local digits according to the country's preferred mask, e.g.
     * `"51985550619"` -> `"(51) 98555-0619"` (BR) or `"5551234567"` -> `"(555) 123-4567"` (US).
     * Extra digits past the mask are appended unformatted.
     */
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

        /**
         * Resolves the user's country from the device locale, falling back to [fallback].
         *
         * Prefer [deviceDefault] with a context: the locale is a language preference, not a location,
         * so someone in Brazil running their phone in English lands on the wrong dial code here.
         */
        val deviceDefault: Country
            get() = fromRegion(Locale.getDefault().country)

        /**
         * GUA FORK: resolves the user's country from the SIM first, then the network they are
         * camped on, and only then the locale.
         *
         * The SIM is the one signal that actually tracks where the number comes from. Locale is a
         * language choice: a Brazilian phone set to English reports US, and the login screen then
         * offers +1 for a +55 number. Network is the middle ground, correct while roaming is not
         * involved and still better than a language setting.
         */
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

        /** Looks up a country by its ISO code (case-insensitive). */
        fun find(isoCode: String): Country? = all.firstOrNull { it.isoCode == isoCode.uppercase() }

        /**
         * Splits an optional pre-populated E.164 number into (country, localDigits). Falls back to the
         * device's locale when the input is empty or unparseable. Longest-prefix dial-code match
         * (some dial codes are 4 digits, e.g. +1876 for Jamaica).
         */
        fun parse(initialPhoneNumber: String, context: Context? = null): Pair<Country, String> {
            val default = deviceDefault(context)
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

        /**
         * Picks the most likely country for the digits the user is currently typing, given their
         * currently selected country. Returns `null` if the current selection is already the best match.
         *
         * 1. Longest-prefix dial-code match against [all] (handles e.g. typing "242" while on US (+1) ->
         *    Bahamas +1242; also pasting a full number starting with a country code).
         * 2. NANP +1 disambiguation: when the dial code is "1" and >=3 local digits are entered, look up
         *    the area code in [canadianAreaCodes] to flip between US and Canada.
         */
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

        /**
         * Normalises raw text the user typed/pasted/autofilled into the *local* number field into a
         * clean (country, localDigits) pair, transparently stripping a redundant country code and
         * switching the country when the input is unambiguously international.
         *
         * Runs on every text change, so it must be a no-op for ordinary local typing.
         * Resolution order:
         *
         * 1. Leading "+" (explicit E.164): longest-prefix dial-code match -> matched country +
         *    remainder as local digits. Always safe to strip because the user signalled intent.
         * 2. No "+", leading digits == selected dial code AND total length is exactly
         *    `dialCode + nationalLength`: the dial code was redundantly included (e.g. +1 selected,
         *    "15551234567"). Strip it, keep the country. Only fires when the country has a known
         *    national length and the remainder can't itself begin with the dial code (NANP national
         *    numbers never start with "1"), keeping it unambiguous.
         * 3. Otherwise: return the digits untouched (only stripped of formatting) so a valid local
         *    number is never mangled.
         */
        fun normalize(rawInput: String, current: Country): Pair<Country, String> {
            val trimmed = rawInput.trim()

            // 1. Explicit international format.
            if (trimmed.startsWith("+")) {
                val digits = trimmed.filter { it.isDigit() }
                for (length in minOf(4, digits.length) downTo 1) {
                    val prefix = digits.take(length)
                    val country = all.firstOrNull { it.dialCode == prefix }
                    if (country != null) {
                        return country to digits.drop(length)
                    }
                }
                // Unknown dial code: keep the current country, drop the leading "+" formatting only.
                return current to digits
            }

            val digits = trimmed.filter { it.isDigit() }

            // 2. Redundant dial code with no "+".
            val dial = current.dialCode
            val nationalLength = current.nationalDigitLength
            if (digits.length > dial.length && digits.startsWith(dial) && nationalLength != null) {
                val remainder = digits.drop(dial.length)
                // Only strip when the remaining digits are exactly a full national number AND the
                // remainder doesn't itself start with the dial code (which would make it ambiguous,
                // e.g. a genuine local number that happens to begin with the dial-code digits).
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
