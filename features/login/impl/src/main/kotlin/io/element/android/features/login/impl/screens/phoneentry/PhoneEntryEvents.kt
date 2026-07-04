/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import io.element.android.libraries.phonenumberentry.Country

/**
 * GUA FORK: UI actions for the phone-first entry screen. Mirrors iOS `PhoneEntryScreenViewAction`.
 */
sealed interface PhoneEntryEvents {
    /** The local phone digits changed; triggers live country auto-detect + national-format masking. */
    data class PhoneNumberChanged(val value: String) : PhoneEntryEvents

    /** A country was picked from the country picker. */
    data class CountrySelected(val country: Country) : PhoneEntryEvents

    /** The user tapped continue; runs resolve -> configure -> OIDC. */
    data object Continue : PhoneEntryEvents

    /** Dismiss the current error. */
    data object ClearError : PhoneEntryEvents
}
