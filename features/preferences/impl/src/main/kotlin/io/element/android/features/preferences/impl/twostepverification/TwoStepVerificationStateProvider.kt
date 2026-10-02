/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.AuthFactor
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.ui.strings.CommonStrings

private val US = Country(isoCode = "US", dialCode = "1")
private val BR = Country(isoCode = "BR", dialCode = "55")

open class TwoStepVerificationStateProvider : PreviewParameterProvider<TwoStepVerificationState> {
    override val values: Sequence<TwoStepVerificationState>
        get() = sequenceOf(
            // Paparazzi shards previews by index: keep the entry count stable, or unrelated goldens churn.
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.Loading),
            aTwoStepVerificationState(factors = aPreviewFactorStatus()),
            aTwoStepVerificationState(factors = aPreviewFactorStatus(hasPin = true)),
            aTwoStepVerificationState(factors = aPreviewFactorStatus(passkeyRegistered = true)),
            aTwoStepVerificationState(factors = null, errorMessage = CommonStrings.error_unknown),
            aTwoStepVerificationState(
                factors = aPreviewFactorStatus(hasPin = true),
                phase = TwoStepVerificationPhase.EnteringCurrent,
                code = "12",
                errorMessage = R.string.screen_two_step_verification_current_incorrect,
            ),
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.EnteringPhone),
            aTwoStepVerificationState(
                phase = TwoStepVerificationPhase.EnteringPhone,
                selectedCountry = US,
                localPhoneNumber = "5551234567",
            ),
            aTwoStepVerificationState(
                phase = TwoStepVerificationPhase.EnteringPhone,
                selectedCountry = BR,
                localPhoneNumber = "11912345678",
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
        )
}

private fun aPreviewFactorStatus(
    hasPin: Boolean = false,
    passkeyRegistered: Boolean = false,
) = AccountFactorStatus(
    hasPin = hasPin,
    passkeyRegistered = passkeyRegistered,
    preferredFactor = when {
        passkeyRegistered -> AuthFactor.PASSKEY
        hasPin -> AuthFactor.PIN
        else -> AuthFactor.PHONE_OTP
    },
    phoneChangeStepUpFactors = listOf(AuthFactor.PASSKEY, AuthFactor.PIN),
    changePhoneCooldownRemainingSeconds = 0,
)

fun aTwoStepVerificationState(
    phase: TwoStepVerificationPhase = TwoStepVerificationPhase.Overview,
    factors: AccountFactorStatus? = aPreviewFactorStatus(),
    code: String = "",
    selectedCountry: Country = US,
    localPhoneNumber: String = "",
    errorMessage: Int? = null,
    showSuccess: Boolean = false,
    factorEnrollUrl: String? = null,
    eventSink: (TwoStepVerificationEvent) -> Unit = {},
) = TwoStepVerificationState(
    phase = phase,
    factors = factors,
    code = code,
    selectedCountry = selectedCountry,
    localPhoneNumber = localPhoneNumber,
    errorMessage = errorMessage,
    showSuccess = showSuccess,
    factorEnrollUrl = factorEnrollUrl,
    eventSink = eventSink,
)
