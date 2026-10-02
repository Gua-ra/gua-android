/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * An authentication factor, as named by the identity service. A passkey is the preferred strong
 * factor and the PIN is the fallback. The phone OTP only proves possession of the number, so it is
 * never enough on its own to re-point that same number.
 */
enum class AuthFactor {
    PASSKEY,
    PIN,
    PHONE_OTP;

    companion object {
        /** Returns null for a factor name this build does not know. Unknown names are dropped, never guessed. */
        fun fromWire(raw: String?): AuthFactor? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * What the account holds, as returned by `GET /security/pin/status`. This is the server's factor
 * signal and the only thing a client should branch on. Whether a registered passkey can be produced
 * on this device may steer the UI but never a security decision.
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
    /**
     * Seconds left on the fresh-2FA hold: a PIN that was just created, changed or reset cannot settle a
     * phone change yet. `0` means no hold. Separate from the minimum gap between two phone changes.
     */
    val changePhoneCooldownRemainingSeconds: Long,
    /**
     * True while a delayed account recovery is live: someone who could not present a factor asked to
     * set a new PIN, and it has neither been cancelled nor run out.
     */
    val accountRecoveryPending: Boolean = false,
    /** When the live recovery can be finished, in epoch seconds. Null when none is live. */
    val accountRecoveryCompletableAtEpochSeconds: Long? = null,
    /** When the live recovery stops being finishable, in epoch seconds. Null when none is live. */
    val accountRecoveryExpiresAtEpochSeconds: Long? = null,
) {
    fun holds(factor: AuthFactor): Boolean = when (factor) {
        AuthFactor.PASSKEY -> passkeyRegistered
        AuthFactor.PIN -> hasPin
        // Every account is reachable on its verified number; nothing to register.
        AuthFactor.PHONE_OTP -> true
    }

    /**
     * The factors a phone change accepts and this account holds, strongest first. Empty means the
     * account can settle no step-up: a hard block, matching the server's `step_up_required` (403).
     */
    val phoneChangeStepUpOptions: List<AuthFactor> get() = phoneChangeStepUpFactors.filter(::holds)

    /** Decides the two-step-verification nudge: a passkey holder already has it. */
    val hasStrongFactor: Boolean get() = passkeyRegistered || hasPin
}
