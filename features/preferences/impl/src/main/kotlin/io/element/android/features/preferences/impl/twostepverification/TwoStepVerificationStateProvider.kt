/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.preferences.impl.R

open class TwoStepVerificationStateProvider : PreviewParameterProvider<TwoStepVerificationState> {
    override val values: Sequence<TwoStepVerificationState>
        get() = sequenceOf(
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.Loading),
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.OverviewNoPin),
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.OverviewHasPin),
            aTwoStepVerificationState(phase = TwoStepVerificationPhase.EnteringPhone, phone = "+1"),
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
    phone: String = "",
    errorMessage: Int? = null,
    showSuccess: Boolean = false,
    eventSink: (TwoStepVerificationEvent) -> Unit = {},
) = TwoStepVerificationState(
    phase = phase,
    code = code,
    phone = phone,
    errorMessage = errorMessage,
    showSuccess = showSuccess,
    eventSink = eventSink,
)
