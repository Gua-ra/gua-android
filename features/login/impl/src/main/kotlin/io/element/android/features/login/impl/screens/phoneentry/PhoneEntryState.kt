/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import io.element.android.features.login.impl.login.LoginMode
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.architecture.AsyncData

/**
 * GUA FORK: state for the phone-first entry screen. Mirrors iOS `PhoneEntryScreenViewState`.
 * The homeserver is never surfaced here — only the user's number and country.
 *
 * @param loginMode reflects the resolve -> configure -> OIDC pipeline run by `LoginHelper`. It is
 * [AsyncData.Loading] while resolving/building the OIDC url, [AsyncData.Success] once the OIDC url is
 * ready (the View hands it to the navigator), and [AsyncData.Failure] on error.
 */
data class PhoneEntryState(
    val selectedCountry: Country,
    val localPhoneNumber: String,
    val loginMode: AsyncData<LoginMode>,
    val eventSink: (PhoneEntryEvents) -> Unit,
) {
    /** User-entered digits, stripped of any non-numeric characters. */
    val localDigits: String get() = localPhoneNumber.filter { it.isDigit() }

    /** Full E.164 phone number to send to the backend (e.g. "+15551234567"). */
    val e164PhoneNumber: String get() = "+" + selectedCountry.dialCode + localDigits

    /** Whether the busy/loading state should be shown (resolving the homeserver and building OIDC url). */
    val isSubmitting: Boolean get() = loginMode is AsyncData.Loading

    /**
     * E.164 numbers are 1-15 digits including the country code. Subscriber number minimum is
     * generally 4 digits, and the Gua resolver requires `+[1-9]\d{6,14}` (>= 7 total digits), so we
     * require at least that, enforce the resolver's minimum total length and cap the total length.
     */
    val canContinue: Boolean
        get() = !isSubmitting && isValid(localDigits = localDigits, dialCode = selectedCountry.dialCode)

    companion object {
        fun isValid(localDigits: String, dialCode: String): Boolean {
            val totalDigits = dialCode.length + localDigits.length
            return localDigits.length >= 4 && totalDigits >= 7 && totalDigits <= 15
        }
    }
}
