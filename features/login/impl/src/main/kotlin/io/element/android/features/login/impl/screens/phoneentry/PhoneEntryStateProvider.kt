/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.login.impl.error.ChangeServerError
import io.element.android.features.login.impl.login.LoginMode
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.architecture.AsyncData

open class PhoneEntryStateProvider : PreviewParameterProvider<PhoneEntryState> {
    override val values: Sequence<PhoneEntryState>
        get() = sequenceOf(
            // Empty US number.
            aPhoneEntryState(),
            // Typed + valid US number (valid per libphonenumber, which gates Continue).
            aPhoneEntryState(localPhoneNumber = "(201) 555-0123"),
            // A non-default-country flag (Brazil), masked.
            aPhoneEntryState(
                selectedCountry = Country(isoCode = "BR", dialCode = "55"),
                localPhoneNumber = "(11) 91234-5678",
            ),
            // Submitting (resolving + building OIDC url).
            aPhoneEntryState(
                localPhoneNumber = "(201) 555-0123",
                loginMode = AsyncData.Loading(),
            ),
            // Error.
            aPhoneEntryState(
                localPhoneNumber = "(201) 555-0123",
                loginMode = AsyncData.Failure(ChangeServerError.InvalidServer),
            ),
        )
}

internal fun aPhoneEntryState(
    selectedCountry: Country = Country(isoCode = "US", dialCode = "1"),
    localPhoneNumber: String = "",
    loginMode: AsyncData<LoginMode> = AsyncData.Uninitialized,
    eventSink: (PhoneEntryEvents) -> Unit = {},
) = PhoneEntryState(
    selectedCountry = selectedCountry,
    localPhoneNumber = localPhoneNumber,
    loginMode = loginMode,
    eventSink = eventSink,
)
