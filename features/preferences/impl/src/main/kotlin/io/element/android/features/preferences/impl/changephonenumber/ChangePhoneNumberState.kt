/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.annotation.StringRes

/**
 * GUA FORK: drives the change-phone-number screen. This is the real backend flow, a single-step
 * change-number where the OTP goes to the NEW number and the account PIN is the second factor.
 *
 * [Intro] -> [EnteringNewPhone] -> [EnteringPin] (sends the OTP) ->
 * [EnteringOtp] (submits the change) -> [Done].
 *
 * [Submitting] is shown while the two async identity-service calls are in flight.
 */
enum class ChangePhoneNumberPhase {
    Intro,
    EnteringNewPhone,
    EnteringPin,
    EnteringOtp,
    Submitting,
    Done,
}

data class ChangePhoneNumberState(
    val phase: ChangePhoneNumberPhase,
    /** The 6-digit code currently being typed (the account PIN or the OTP). */
    val code: String,
    /** The E.164 phone number typed during the flow (e.g. "+15551234567"). */
    val phone: String,
    /** Resource id of the error to surface under the field, or null. */
    @StringRes val errorMessage: Int?,
    val eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    val isWorking: Boolean = phase == ChangePhoneNumberPhase.Submitting

    val canContinue: Boolean = when (phase) {
        ChangePhoneNumberPhase.Intro -> true
        ChangePhoneNumberPhase.EnteringNewPhone -> isValidPhone(phone) && !isWorking
        ChangePhoneNumberPhase.EnteringPin,
        ChangePhoneNumberPhase.EnteringOtp -> code.length == CODE_LENGTH && !isWorking
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
