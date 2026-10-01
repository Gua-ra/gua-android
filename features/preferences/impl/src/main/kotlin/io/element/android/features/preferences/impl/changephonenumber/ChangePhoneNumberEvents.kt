/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import io.element.android.libraries.phonenumberentry.Country

sealed interface ChangePhoneNumberEvents {
    data class CodeChanged(val code: String) : ChangePhoneNumberEvents

    data class PhoneChanged(val value: String) : ChangePhoneNumberEvents

    data object SelectCountry : ChangePhoneNumberEvents

    data class CountrySelected(val country: Country) : ChangePhoneNumberEvents

    data object Continue : ChangePhoneNumberEvents

    data object SetUpPin : ChangePhoneNumberEvents

    data object SetUpPasskey : ChangePhoneNumberEvents

    data object ClearPasskeyEnrollUrl : ChangePhoneNumberEvents

    data object CancelEntry : ChangePhoneNumberEvents

    data object Done : ChangePhoneNumberEvents
}
