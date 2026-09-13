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
 * GUA FORK: drives the two-step-verification (account PIN) screen between the overview state and the
 * multi-step PIN flows. Android counterpart of iOS `TwoStepVerificationScreenPhase`.
 *
 * Setup flow (no existing PIN):  [EnteringNew] -> [ConfirmingNew] -> [Submitting].
 *
 * Change flow (existing PIN, PIN-FIRST then OTP-protected): we verify the current PIN BEFORE any SMS
 * is sent, so identity is proven before the phone is confirmed:
 * [EnteringCurrent] (verified live with the backend) -> [EnteringPhone] (confirm the on-file number,
 * which fires the OTP) -> [EnteringOtp] -> [EnteringNew] -> [ConfirmingNew] -> [Submitting].
 */
enum class TwoStepVerificationPhase {
    Loading,

    /**
     * The landing state. What it says comes from [TwoStepVerificationState.factors], not from a lone
     * `hasPin`: an account with a passkey and no PIN already has two-step verification, and a
     * factor status that could not be read is unknown rather than absent. Splitting this into
     * has-PIN and no-PIN phases is what previously forced a failed read to claim "off".
     */
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
    /**
     * The factors the account holds, per the server. Null means the status could not be read, which
     * is UNKNOWN and must not be rendered as "you have no two-step verification".
     */
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
     * Set to the authenticated passkey-enrollment URL once [TwoStepVerificationEvent.SetUpPasskey]
     * resolves, so the View can open it in a Chrome Custom Tab (the authenticated web ceremony,
     * mirroring iOS' ASWebAuthenticationSession). Cleared via
     * [TwoStepVerificationEvent.ClearPasskeyEnrollUrl] once opened.
     */
    val passkeyEnrollUrl: String?,
    val eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    val isWorking: Boolean = phase == TwoStepVerificationPhase.Submitting

    /**
     * True once the account is known to hold a PIN, false once it is known not to, and null while
     * the status could not be read. Drives "Change PIN" versus "Set up PIN", and UNKNOWN must get
     * neither: collapsing it to "no PIN" is what offered "Set up PIN" to a PIN holder and then sent
     * them into the initial-PIN call the server refuses.
     */
    val hasPin: Boolean? = factors?.hasPin

    /**
     * True once the account is known to hold a passkey, false once it is known not to, null while
     * the status could not be read. Unknown offers no enrollment: the ceremony excludes credentials
     * the account already holds, so guessing "none" here just sends the user to an authenticator
     * that refuses.
     */
    val passkeyRegistered: Boolean? = factors?.passkeyRegistered

    /**
     * Whether two-step verification is on, off, or not known. A passkey alone turns it on: that is
     * the whole point of the preferred factor, and reporting "off" to its holder is what sent them
     * to create a PIN they do not need.
     */
    val twoStepVerificationOn: Boolean? = factors?.hasStrongFactor

    /** Confirm-number digits, stripped of any formatting. */
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
