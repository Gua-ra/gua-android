/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.element.android.features.login.impl.login.LoginMode
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.phonenumberentry.Country

/**
 * GUA FORK: state for the phone-first entry screen. Mirrors iOS `PhoneEntryScreenViewState`.
 * The homeserver is never surfaced here — only the user's number and country.
 *
 * @param selectedCountry the country whose dial code and national-format mask apply to the input.
 * @param localPhoneNumber the user-typed national number as raw digits. The country's display mask
 * is applied visually by the field, so the buffer never carries formatting characters.
 * @param loginMode reflects the resolve -> configure -> OIDC pipeline run by `LoginHelper`. It is
 * [AsyncData.Loading] while resolving/building the OIDC url, [AsyncData.Success] once the OIDC url is
 * ready (the View hands it to the navigator), and [AsyncData.Failure] on error.
 * @param eventSink receives the [PhoneEntryEvents] emitted by the View.
 */
data class PhoneEntryState(
    val selectedCountry: Country,
    val localPhoneNumber: String,
    val loginMode: AsyncData<LoginMode>,
    val eventSink: (PhoneEntryEvents) -> Unit,
) {
    /** User-entered digits, stripped of any non-numeric characters. */
    val localDigits: String get() = localPhoneNumber.filter { it.isDigit() }

    /** Full E.164 phone number to send to the backend (e.g. "+12015550123"). */
    val e164PhoneNumber: String get() = "+" + selectedCountry.dialCode + localDigits

    /** Whether the busy/loading state should be shown (resolving the homeserver and building OIDC url). */
    val isSubmitting: Boolean get() = loginMode is AsyncData.Loading

    /**
     * Mirrors the backend gate (identity-service `PhoneNumberNormalizer`): the same libphonenumber
     * `isValidNumber` check, on the same E.164 string the client submits. Gating Continue on the
     * exact backend rule means this screen can never accept a number the backend would then reject
     * (the previous length-only heuristic let through numbers like +1 555 123 4567).
     */
    val canContinue: Boolean
        get() = !isSubmitting && isValid(localDigits = localDigits, dialCode = selectedCountry.dialCode)

    companion object {
        fun isValid(localDigits: String, dialCode: String): Boolean {
            if (localDigits.isEmpty()) return false
            val phoneNumberUtil = PhoneNumberUtil.getInstance()
            return try {
                // The number carries an explicit +<dial code>, so no default region is needed.
                phoneNumberUtil.isValidNumber(phoneNumberUtil.parse("+$dialCode$localDigits", null))
            } catch (exception: NumberParseException) {
                false
            }
        }
    }
}
