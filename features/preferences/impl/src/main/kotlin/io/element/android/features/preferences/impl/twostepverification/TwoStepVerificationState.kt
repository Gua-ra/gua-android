/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.annotation.StringRes

/**
 * GUA FORK: drives the two-step-verification (account PIN) screen between the overview state and the
 * multi-step PIN flows. Android counterpart of iOS `TwoStepVerificationScreenPhase`.
 *
 * Setup flow (no existing PIN):  [EnteringNew] -> [ConfirmingNew] -> [Submitting].
 *
 * Change flow (existing PIN, OTP-protected):
 * [EnteringPhone] -> [EnteringCurrent] (verified live with the backend) ->
 * [EnteringOtp] -> [EnteringNew] -> [ConfirmingNew] -> [Submitting].
 */
enum class TwoStepVerificationPhase {
    Loading,
    OverviewNoPin,
    OverviewHasPin,
    EnteringPhone,
    EnteringCurrent,
    EnteringOtp,
    EnteringNew,
    ConfirmingNew,
    Submitting,
}

data class TwoStepVerificationState(
    val phase: TwoStepVerificationPhase,
    /** The 6-digit code currently being typed (current PIN, OTP, new PIN or confirmation). */
    val code: String,
    /** The E.164 phone number typed during the change flow (e.g. "+15551234567"). */
    val phone: String,
    /** Resource id of the error to surface under the field, or null. */
    @StringRes val errorMessage: Int?,
    /** Set after a PIN was successfully set or changed, so the View can show a confirmation. */
    val showSuccess: Boolean,
    val eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    val isWorking: Boolean = phase == TwoStepVerificationPhase.Submitting

    val canContinue: Boolean = when (phase) {
        TwoStepVerificationPhase.EnteringPhone -> isValidPhone(phone) && !isWorking
        TwoStepVerificationPhase.EnteringCurrent,
        TwoStepVerificationPhase.EnteringNew,
        TwoStepVerificationPhase.ConfirmingNew,
        TwoStepVerificationPhase.EnteringOtp -> code.length == CODE_LENGTH && !isWorking
        else -> false
    }

    companion object {
        const val CODE_LENGTH = 6

        fun isValidPhone(phone: String): Boolean {
            val trimmed = phone.trim()
            if (!trimmed.startsWith("+")) return false
            val digits = trimmed.drop(1)
            return digits.length in 8..15 && digits.all { it.isDigit() }
        }
    }
}
