/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import io.element.android.libraries.phonenumberentry.Country

sealed interface PhoneEntryEvents {
    /** The local phone digits changed; triggers live country auto-detect + national-format masking. */
    data class PhoneNumberChanged(val value: String) : PhoneEntryEvents

    data class CountrySelected(val country: Country) : PhoneEntryEvents

    /** The user tapped continue; runs resolve -> configure -> OIDC. */
    data object Continue : PhoneEntryEvents

    /**
     * The user chose to sign in with a passkey instead of a number. Deliberately carries no phone
     * number: the credential is discoverable, so it identifies the account by itself and no code
     * has to be sent to reach it. Runs configure (default account provider) -> OIDC.
     */
    data object SignInWithPasskey : PhoneEntryEvents

    data object ClearError : PhoneEntryEvents
}
