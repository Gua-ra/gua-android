/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.annotation.StringRes
import io.element.android.libraries.phonenumberentry.Country

/**
 * GUA FORK: drives the change-phone-number screen. This is the real backend flow, PIN-first: the
 * user confirms their account PIN up front (yielding a short-lived reauth token), then enters the
 * NEW number (which triggers the OTP), then enters that OTP to complete the change. The SMS does NOT
 * fire until a valid reauth token exists.
 *
 * On [Intro] Continue we FIRST fetch the PIN status (a WhatsApp belt-and-suspenders gate):
 *  - no PIN set -> [NeedsPinSetup] interstitial (route to the 2SV PIN-setup flow), never proceed.
 *  - a fresh-2FA cooldown is active -> [Cooldown] interstitial, never proceed.
 *  - otherwise -> [EnteringPin] (step-up, no SMS) -> [EnteringNewPhone] (sends the OTP) ->
 *    [EnteringOtp] (submits the change) -> [Done].
 *
 * [Submitting] is shown while the async identity-service calls are in flight.
 */
enum class ChangePhoneNumberPhase {
    Intro,
    NeedsPinSetup,
    Cooldown,
    EnteringPin,
    EnteringNewPhone,
    EnteringOtp,
    Submitting,
    Done,
}

data class ChangePhoneNumberState(
    val phase: ChangePhoneNumberPhase,
    /** The 6-digit code currently being typed (the account PIN or the OTP). */
    val code: String,
    /** The country selected for the NEW number (drives the dial code, flag and national mask). */
    val selectedCountry: Country,
    /** The local (national-format) digits the user typed for the NEW number, e.g. "(555) 123-4567". */
    val localPhoneNumber: String,
    /** Resource id of the error to surface under the field, or null. */
    @StringRes val errorMessage: Int?,
    /**
     * Remaining seconds of the fresh-2FA cooldown, surfaced (humanised) on the [ChangePhoneNumberPhase.Cooldown]
     * interstitial. 0 outside that phase.
     */
    val cooldownRemainingSeconds: Long,
    val eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    val isWorking: Boolean = phase == ChangePhoneNumberPhase.Submitting

    /** New-number digits, stripped of any formatting. */
    val localDigits: String get() = localPhoneNumber.filter { it.isDigit() }

    /** Full E.164 number to send to the backend (e.g. "+15551234567"). */
    val e164PhoneNumber: String get() = "+" + selectedCountry.dialCode + localDigits

    val canContinue: Boolean = when (phase) {
        ChangePhoneNumberPhase.Intro -> true
        ChangePhoneNumberPhase.EnteringNewPhone ->
            isValidNumber(localDigits = localPhoneNumber.filter { it.isDigit() }, dialCode = selectedCountry.dialCode) && !isWorking
        ChangePhoneNumberPhase.EnteringPin,
        ChangePhoneNumberPhase.EnteringOtp -> code.length == CODE_LENGTH && !isWorking
        else -> false
    }

    companion object {
        const val CODE_LENGTH = 6

        /**
         * Mirror of the welcome `PhoneEntryState` rule: at least 4 local digits, and a total length
         * (dial code + local digits) within the E.164 7..15 window the Gua resolver requires.
         */
        fun isValidNumber(localDigits: String, dialCode: String): Boolean {
            val totalDigits = dialCode.length + localDigits.length
            return localDigits.length >= 4 && totalDigits in 7..15
        }
    }
}
