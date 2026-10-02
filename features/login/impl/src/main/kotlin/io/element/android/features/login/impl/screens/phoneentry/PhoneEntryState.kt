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
 * State for the phone-first entry screen. The homeserver is never surfaced here.
 *
 * [localPhoneNumber] is the national number as raw digits. The country's display mask is applied
 * visually by the field. [loginMode] reflects the resolve -> configure -> OIDC pipeline run by
 * `LoginHelper`: [AsyncData.Loading] while resolving, [AsyncData.Success] once the OIDC url is ready,
 * [AsyncData.Failure] on error.
 */
data class PhoneEntryState(
    val selectedCountry: Country,
    val localPhoneNumber: String,
    val loginMode: AsyncData<LoginMode>,
    val eventSink: (PhoneEntryEvents) -> Unit,
) {
    val localDigits: String get() = localPhoneNumber.filter { it.isDigit() }

    /** Full E.164 phone number to send to the backend (e.g. "+12015550123"). */
    val e164PhoneNumber: String get() = "+" + selectedCountry.dialCode + localDigits

    val isSubmitting: Boolean get() = loginMode is AsyncData.Loading

    /**
     * Mirrors the backend gate (identity-service `PhoneNumberNormalizer`): the same libphonenumber
     * `isValidNumber` check on the same E.164 string, so this screen never accepts a number the backend
     * would then reject.
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
