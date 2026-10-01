/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import io.element.android.libraries.phonenumberentry.Country

sealed interface PhoneEntryEvents {
    data class PhoneNumberChanged(val value: String) : PhoneEntryEvents

    data class CountrySelected(val country: Country) : PhoneEntryEvents

    data object Continue : PhoneEntryEvents

    data object SignInWithPasskey : PhoneEntryEvents

    data object ClearError : PhoneEntryEvents
}
