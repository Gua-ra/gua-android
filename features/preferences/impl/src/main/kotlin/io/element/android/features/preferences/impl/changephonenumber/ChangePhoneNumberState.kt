/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.annotation.StringRes
import io.element.android.libraries.phonenumberentry.Country

/**
 * Phases of the change-phone-number flow.
 *
 * On [Intro] Continue the account's factors are fetched first and the flow branches before anything
 * is sent:
 *  - no factor a phone change accepts -> [NeedsStepUp], a hard block.
 *  - a fresh-2FA hold or a phone-change cooldown -> [Cooldown].
 *  - otherwise -> [EnteringCurrentPhone].
 *
 * [EnteringCurrentPhone] asks which number is on the account, [EnteringReauthOtp] takes the code that
 * number received, [EnteringPin] captures the step-up factor and [EnteringNewPhone] takes the new
 * number. Submitting the pair spends the token and the factor in one call, and only that call texts
 * the new number. [Submitting] is shown while identity-service calls are in flight.
 */
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

/**
 * Why the flow stopped at [ChangePhoneNumberPhase.NeedsStepUp], and therefore which way out to offer.
 * Both are hard blocks: neither lets the change continue.
 */
enum class StepUpBlock {
    /**
     * The account holds neither a passkey nor a PIN, so it can settle no step-up at all. Client-side twin
     * of the server's `step_up_required` (403). The way out is to register a passkey (preferred) or a PIN.
     */
    NoFactorRegistered,

    /**
     * The account's only accepted factor is a passkey, and this build has no WebAuthn ceremony to assert
     * it with. The PIN is offered as the account's own fallback factor.
     */
    PasskeyNotUsableHere,
}

data class ChangePhoneNumberState(
    val phase: ChangePhoneNumberPhase,
    /** The 6-digit code currently being typed (a reauth OTP, the account PIN, or the new-number OTP). */
    val code: String,
    /**
     * The country selected in whichever phone step is on screen, the current number or the new one.
     * One field because only one of those steps is ever showing.
     */
    val selectedCountry: Country,
    /** The local (national-format) digits typed in that step, e.g. "(555) 123-4567". */
    val localPhoneNumber: String,
    /** Resource id of the error to surface under the field, or null. */
    @StringRes val errorMessage: Int?,
    /**
     * Remaining seconds of the active cooldown, surfaced (humanised) on the
     * [ChangePhoneNumberPhase.Cooldown] interstitial. 0 outside that phase.
     */
    val cooldownRemainingSeconds: Long,
    /** Why the flow is blocked, set only in [ChangePhoneNumberPhase.NeedsStepUp]. */
    val stepUpBlock: StepUpBlock?,
    /**
     * Set to the authenticated passkey-enrollment URL once [ChangePhoneNumberEvents.SetUpPasskey]
     * resolves, so the View can open it in a Chrome Custom Tab. Cleared via
     * [ChangePhoneNumberEvents.ClearPasskeyEnrollUrl] once opened.
     */
    val passkeyEnrollUrl: String?,
    val eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    val isWorking: Boolean = phase == ChangePhoneNumberPhase.Submitting

    val localDigits: String get() = localPhoneNumber.filter { it.isDigit() }

    /** Full E.164 number to send to the backend (e.g. "+15551234567"). */
    val e164PhoneNumber: String get() = "+" + selectedCountry.dialCode + localDigits

    /**
     * Whether to offer registering a passkey as the way out of the block. Only when the account has
     * no factor at all: an account that already holds a passkey cannot enroll a second one, the
     * ceremony excludes the credentials it already has, so offering it there would be a dead end.
     */
    val canSetUpPasskey: Boolean = stepUpBlock == StepUpBlock.NoFactorRegistered

    /** Whether to offer setting up a PIN as the way out of the block. Both blocks allow it. */
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
