/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.annotation.StringRes
import io.element.android.libraries.phonenumberentry.Country

enum class ChangePhoneNumberPhase {
    Intro,
    NeedsStepUp,
    Cooldown,
    EnteringCurrentPhone,
    EnteringReauthOtp,
    EnteringPin,
    EnteringNewPhone,
    EnteringOtp,
    Submitting,
    Done,
}

enum class StepUpBlock {
    /** Client-side twin of the server's `step_up_required` (403). */
    NoFactorRegistered,

    /** The only accepted factor is a passkey, which this build cannot assert. */
    PasskeyNotUsableHere,
}

data class ChangePhoneNumberState(
    val phase: ChangePhoneNumberPhase,
    val code: String,
    val selectedCountry: Country,
    val localPhoneNumber: String,
    @StringRes val errorMessage: Int?,
    val cooldownRemainingSeconds: Long,
    val stepUpBlock: StepUpBlock?,
    val passkeyEnrollUrl: String?,
    val eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    val isWorking: Boolean = phase == ChangePhoneNumberPhase.Submitting

    val localDigits: String get() = localPhoneNumber.filter { it.isDigit() }

    val e164PhoneNumber: String get() = "+" + selectedCountry.dialCode + localDigits

    /** Only with no factor at all: an account that holds a passkey cannot enroll a second one. */
    val canSetUpPasskey: Boolean = stepUpBlock == StepUpBlock.NoFactorRegistered

    val canSetUpPin: Boolean = stepUpBlock != null

    val canContinue: Boolean = when (phase) {
        ChangePhoneNumberPhase.Intro -> true
        ChangePhoneNumberPhase.EnteringCurrentPhone,
        ChangePhoneNumberPhase.EnteringNewPhone ->
            isValidNumber(localDigits = localPhoneNumber.filter { it.isDigit() }, dialCode = selectedCountry.dialCode) && !isWorking
        ChangePhoneNumberPhase.EnteringReauthOtp,
        ChangePhoneNumberPhase.EnteringPin,
        ChangePhoneNumberPhase.EnteringOtp -> code.length == CODE_LENGTH && !isWorking
        else -> false
    }

    companion object {
        const val CODE_LENGTH = 6

        /** At least 4 local digits and a total within the E.164 7..15 window the resolver requires. */
        fun isValidNumber(localDigits: String, dialCode: String): Boolean {
            val totalDigits = dialCode.length + localDigits.length
            return localDigits.length >= 4 && totalDigits in 7..15
        }
    }
}
