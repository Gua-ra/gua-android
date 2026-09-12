/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: an authentication factor, as named by the identity service.
 *
 * A passkey is the preferred strong factor; the PIN is the fallback for everyone who cannot produce
 * one; the phone OTP is possession of the number and is never on its own enough to re-point that
 * same number.
 */
enum class AuthFactor {
    PASSKEY,
    PIN,
    PHONE_OTP;

    companion object {
        /**
         * Parse a factor name as sent by the identity service, returning null for anything this
         * build does not know. Unknown names are dropped rather than guessed: a factor we cannot
         * name is a factor we cannot produce.
         */
        fun fromWire(raw: String?): AuthFactor? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * GUA FORK: what the account holds, as returned by `GET /security/pin/status`. Android counterpart
 * of the iOS account factor status.
 *
 * This is the server's factor signal and the only thing a client should branch on. Registration is
 * server truth. Whether a registered passkey can actually be produced on THIS device is something
 * only the client knows, is never sent back, and may steer the UI but never a security decision.
 *
 * [changePhoneCooldownRemainingSeconds] is the fresh-2FA hold: after a PIN is created, changed or
 * reset it cannot be spent as the phone-change step-up for a window, so someone who just set a PIN
 * cannot immediately use it to take over the number. `0` means no active hold. It is separate from
 * the minimum gap between two successful phone changes, which the change endpoint reports itself.
 */
data class AccountFactorStatus(
    /** True once the account has configured a 6-digit account PIN. */
    val hasPin: Boolean,
    /** True when the account has at least one passkey registered and the deployment has them enabled. */
    val passkeyRegistered: Boolean,
    /** The strongest factor the account holds, and therefore the one to offer first. */
    val preferredFactor: AuthFactor,
    /** The factors a phone change accepts as its step-up, strongest first. */
    val phoneChangeStepUpFactors: List<AuthFactor>,
    /** Seconds still to run on the fresh-2FA hold before the PIN may settle a phone change. */
    val changePhoneCooldownRemainingSeconds: Long,
) {
    /** Whether the account holds [factor] right now. */
    fun holds(factor: AuthFactor): Boolean = when (factor) {
        AuthFactor.PASSKEY -> passkeyRegistered
        AuthFactor.PIN -> hasPin
        // Every account is reachable on its verified number; nothing to register.
        AuthFactor.PHONE_OTP -> true
    }

    /**
     * The factors a phone change accepts AND this account actually holds, strongest first. Empty
     * means the account can settle no step-up at all, which the server answers with
     * `step_up_required` (403) and which the client must treat as a hard block.
     */
    val phoneChangeStepUpOptions: List<AuthFactor> get() = phoneChangeStepUpFactors.filter(::holds)

    /**
     * Whether the account holds any strong factor at all. This, not [hasPin], is what decides
     * whether to nudge someone to set up two-step verification: a passkey holder already has it.
     */
    val hasStrongFactor: Boolean get() = passkeyRegistered || hasPin
}
