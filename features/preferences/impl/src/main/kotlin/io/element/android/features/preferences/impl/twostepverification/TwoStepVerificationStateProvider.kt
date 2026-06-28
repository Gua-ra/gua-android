/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.phonenumberentry.Country

private val US = Country(isoCode = "US", dialCode = "1")
private val BR = Country(isoCode = "BR", dialCode = "55")

open class TwoStepVerificationStateProvider : PreviewParameterProvider<TwoStepVerificationState> {
    override val values: Sequence<TwoStepVerificationState>
        get() = sequenceOf(
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.Loading),
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.OverviewNoPin),
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.OverviewHasPin),
            // PIN-first change flow: the current PIN is verified BEFORE the phone is confirmed.
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.EnteringCurrent, code = "123"),
            aTwoStepVerificationState(
                phase = TwoStepVerificationPhase.EnteringCurrent,
                code = "12",
                errorMessage = R.string.screen_two_step_verification_current_incorrect,
            ),
            // Confirm-number step with the shared picker field: empty (placeholder) and a filled US number.
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.EnteringPhone),
            aTwoStepVerificationState(
                phase = TwoStepVerificationPhase.EnteringPhone,
                selectedCountry = US,
                localPhoneNumber = US.formatNational("5551234567"),
            ),
            // Confirm-number step with a different country selected (flag + dial code change).
            aTwoStepVerificationState(
                phase = TwoStepVerificationPhase.EnteringPhone,
                selectedCountry = BR,
                localPhoneNumber = BR.formatNational("11912345678"),
            ),
            aTwoStepVerificationState(
                phase = TwoStepVerificationPhase.EnteringPhone,
                selectedCountry = US,
                localPhoneNumber = "5",
                errorMessage = R.string.screen_two_step_verification_phone_invalid,
            ),
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.EnteringNew, code = "123"),
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.EnteringOtp, code = "1234"),
            aTwoStepVerificationState(
                phase = TwoStepVerificationPhase.ConfirmingNew,
                code = "12",
                errorMessage = R.string.screen_two_step_verification_mismatch_error,
            ),
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.Submitting, code = "123456"),
        )
}

fun aTwoStepVerificationState(
    phase: TwoStepVerificationPhase = TwoStepVerificationPhase.OverviewNoPin,
    code: String = "",
    selectedCountry: Country = US,
    localPhoneNumber: String = "",
    errorMessage: Int? = null,
    showSuccess: Boolean = false,
    eventSink: (TwoStepVerificationEvent) -> Unit = {},
) = TwoStepVerificationState(
    phase = phase,
    code = code,
    selectedCountry = selectedCountry,
    localPhoneNumber = localPhoneNumber,
    errorMessage = errorMessage,
    showSuccess = showSuccess,
    eventSink = eventSink,
)
