/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.phonenumberentry.Country

private val US = Country(isoCode = "US", dialCode = "1")
private val BR = Country(isoCode = "BR", dialCode = "55")

open class ChangePhoneNumberStateProvider : PreviewParameterProvider<ChangePhoneNumberState> {
    override val values: Sequence<ChangePhoneNumberState>
        get() = sequenceOf(
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.Intro),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.NeedsPinSetup),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.Cooldown,
                // 6 days, 3 hours -> exercises the multi-unit humaniser.
                cooldownRemainingSeconds = 6L * 24 * 3600 + 3 * 3600,
            ),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringPin, code = "123"),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringPin,
                code = "12",
                errorMessage = R.string.screen_change_phone_pin_incorrect,
            ),
            // New-number step: empty (shows the placeholder) and a filled US number.
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringNewPhone),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringNewPhone,
                selectedCountry = US,
                localPhoneNumber = US.formatNational("5551234567"),
            ),
            // New-number step with a different country selected (flag + dial code change).
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringNewPhone,
                selectedCountry = BR,
                localPhoneNumber = BR.formatNational("11912345678"),
            ),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringNewPhone,
                selectedCountry = US,
                localPhoneNumber = "5",
                errorMessage = R.string.screen_change_phone_new_invalid,
            ),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringOtp, code = "1234"),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringOtp,
                code = "12",
                errorMessage = R.string.screen_change_phone_otp_invalid,
            ),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.Submitting, code = "123456"),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.Done),
        )
}

fun aChangePhoneNumberState(
    phase: ChangePhoneNumberPhase = ChangePhoneNumberPhase.Intro,
    code: String = "",
    selectedCountry: Country = US,
    localPhoneNumber: String = "",
    errorMessage: Int? = null,
    cooldownRemainingSeconds: Long = 0,
    eventSink: (ChangePhoneNumberEvents) -> Unit = {},
) = ChangePhoneNumberState(
    phase = phase,
    code = code,
    selectedCountry = selectedCountry,
    localPhoneNumber = localPhoneNumber,
    errorMessage = errorMessage,
    cooldownRemainingSeconds = cooldownRemainingSeconds,
    eventSink = eventSink,
)
