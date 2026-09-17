/*
 * Copyright 2026 Gua
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

    /**
     * The local digits in whichever phone field is on screen changed, the current number or the
     * new one; triggers auto-detect + national masking. Both steps reuse the one field.
     */
    data class PhoneChanged(val value: String) : ChangePhoneNumberEvents

    /** The user tapped the country-selector pill; the Node opens the shared country picker. */
    data object SelectCountry : ChangePhoneNumberEvents

    /** A country was picked from the shared country picker. */
    data class CountrySelected(val country: Country) : ChangePhoneNumberEvents

    /** The user tapped the primary "Continue" button. */
    data object Continue : ChangePhoneNumberEvents

    /** The user chose "Set up PIN" on the step-up block; the Node opens the 2SV PIN-setup flow. */
    data object SetUpPin : ChangePhoneNumberEvents

    /**
     * The user chose "Set up a passkey" on the step-up block. Fetches the authenticated
     * web-ceremony URL; the View opens it in a Chrome Custom Tab, as the 2SV screen does.
     */
    data object SetUpPasskey : ChangePhoneNumberEvents

    /** The passkey enrollment URL has been opened; clear it so it is not opened twice. */
    data object ClearPasskeyEnrollUrl : ChangePhoneNumberEvents

    /** The user cancelled the in-progress flow. */
    data object CancelEntry : ChangePhoneNumberEvents

    /** The user tapped "Done" on the success screen; finish and pop back to settings. */
    data object Done : ChangePhoneNumberEvents
}
