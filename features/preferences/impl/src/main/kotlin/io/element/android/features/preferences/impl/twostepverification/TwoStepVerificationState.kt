/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.annotation.StringRes
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.phonenumberentry.Country

/**
 * Phases of the two-step-verification screen.
 *
 * Setting the first PIN has no phase here: it leaves for the authenticated web ceremony, like a passkey.
 *
 * The change flow verifies the current PIN before any SMS is sent:
 * [EnteringCurrent] -> [EnteringPhone] (confirm the on-file number, which fires the OTP) ->
 * [EnteringOtp] -> [EnteringNew] -> [ConfirmingNew] -> [Submitting].
 */
enum class TwoStepVerificationPhase {
    Loading,

    /** The landing state. What it says comes from [TwoStepVerificationState.factors], not from a lone `hasPin`. */
    Overview,
    EnteringCurrent,
    EnteringPhone,
    EnteringOtp,
    EnteringNew,
    ConfirmingNew,
    Submitting,
}

data class TwoStepVerificationState(
    val phase: TwoStepVerificationPhase,
    /** Null means the status could not be read: unknown, never "no two-step verification". */
    val factors: AccountFactorStatus?,
    /** The 6-digit code currently being typed (current PIN, OTP, new PIN or confirmation). */
    val code: String,
    /** The country selected for the on-file number (drives the dial code, flag and national mask). */
    val selectedCountry: Country,
    /** The local (national-format) digits the user typed to confirm their number, e.g. "(555) 123-4567". */
    val localPhoneNumber: String,
    /** Resource id of the error to surface under the field, or null. */
    @StringRes val errorMessage: Int?,
    /** Set after a PIN was successfully set or changed, so the View can show a confirmation. */
    val showSuccess: Boolean,
    /**
     * The authenticated enrollment URL, set once [TwoStepVerificationEvent.SetUpPasskey] or
     * [TwoStepVerificationEvent.StartSetup] resolves so the View can open it in a Chrome Custom Tab.
     * Cleared through [TwoStepVerificationEvent.ClearFactorEnrollUrl] once opened.
     */
    val factorEnrollUrl: String?,
    val eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    val isWorking: Boolean = phase == TwoStepVerificationPhase.Submitting

    /**
     * True once the account is known to hold a PIN, false once it is known not to, and null while the
     * status could not be read. Drives "Change PIN" versus "Set up PIN". Unknown gets neither.
     */
    val hasPin: Boolean? = factors?.hasPin

    /**
     * True once the account is known to hold a passkey, false once it is known not to, null while the
     * status could not be read. Unknown offers no enrollment.
     */
    val passkeyRegistered: Boolean? = factors?.passkeyRegistered

    /** Whether two-step verification is on, off, or not known. A passkey alone turns it on. */
    val twoStepVerificationOn: Boolean? = factors?.hasStrongFactor

    val localDigits: String get() = localPhoneNumber.filter { it.isDigit() }

    /** Full E.164 number to send to the backend (e.g. "+15551234567"). */
    val e164PhoneNumber: String get() = "+" + selectedCountry.dialCode + localDigits

    val canContinue: Boolean = when (phase) {
        TwoStepVerificationPhase.EnteringPhone -> isValidNumber(localDigits = localDigits, dialCode = selectedCountry.dialCode) && !isWorking
        TwoStepVerificationPhase.EnteringCurrent,
        TwoStepVerificationPhase.EnteringNew,
        TwoStepVerificationPhase.ConfirmingNew,
        TwoStepVerificationPhase.EnteringOtp -> code.length == CODE_LENGTH && !isWorking
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
