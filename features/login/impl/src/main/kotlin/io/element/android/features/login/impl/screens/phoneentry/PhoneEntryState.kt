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

data class PhoneEntryState(
    val selectedCountry: Country,
    val localPhoneNumber: String,
    val loginMode: AsyncData<LoginMode>,
    val eventSink: (PhoneEntryEvents) -> Unit,
) {
    val localDigits: String get() = localPhoneNumber.filter { it.isDigit() }

    val e164PhoneNumber: String get() = "+" + selectedCountry.dialCode + localDigits

    val isSubmitting: Boolean get() = loginMode is AsyncData.Loading

    /** The same libphonenumber `isValidNumber` check the identity service applies. */
    val canContinue: Boolean
        get() = !isSubmitting && isValid(localDigits = localDigits, dialCode = selectedCountry.dialCode)

    companion object {
        fun isValid(localDigits: String, dialCode: String): Boolean {
            if (localDigits.isEmpty()) return false
            val phoneNumberUtil = PhoneNumberUtil.getInstance()
            return try {
                phoneNumberUtil.isValidNumber(phoneNumberUtil.parse("+$dialCode$localDigits", null))
            } catch (exception: NumberParseException) {
                false
            }
        }
    }
}
