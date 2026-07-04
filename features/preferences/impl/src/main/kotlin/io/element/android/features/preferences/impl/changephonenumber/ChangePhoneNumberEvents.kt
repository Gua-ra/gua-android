/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import io.element.android.libraries.phonenumberentry.Country

/**
 * GUA FORK: UI actions for the change-phone-number screen.
 */
sealed interface ChangePhoneNumberEvents {
    /** The user edited the 6-digit code field (account PIN or OTP). */
    data class CodeChanged(val code: String) : ChangePhoneNumberEvents

    /** The local digits in the new-number field changed; triggers auto-detect + national masking. */
    data class PhoneChanged(val value: String) : ChangePhoneNumberEvents

    /** The user tapped the country-selector pill; the Node opens the shared country picker. */
    data object SelectCountry : ChangePhoneNumberEvents

    /** A country was picked from the shared country picker. */
    data class CountrySelected(val country: Country) : ChangePhoneNumberEvents

    /** The user tapped the primary "Continue" button. */
    data object Continue : ChangePhoneNumberEvents

    /** The user cancelled the in-progress flow. */
    data object CancelEntry : ChangePhoneNumberEvents

    /** The user tapped "Done" on the success screen; finish and pop back to settings. */
    data object Done : ChangePhoneNumberEvents
}
