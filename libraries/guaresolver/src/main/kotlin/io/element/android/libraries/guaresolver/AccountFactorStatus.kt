/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

enum class AuthFactor {
    PASSKEY,
    PIN,
    PHONE_OTP;

    companion object {
        fun fromWire(raw: String?): AuthFactor? = entries.firstOrNull { it.name == raw }
    }
}

/** The server's factor signal. Whether a passkey can be produced on this device may steer the UI but never a security decision. */
data class AccountFactorStatus(
    val hasPin: Boolean,
    val passkeyRegistered: Boolean,
    val preferredFactor: AuthFactor,
    val phoneChangeStepUpFactors: List<AuthFactor>,
    val changePhoneCooldownRemainingSeconds: Long,
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

    /** Empty means the account can settle no step-up: a hard block, matching the server's `step_up_required` (403). */
    val phoneChangeStepUpOptions: List<AuthFactor> get() = phoneChangeStepUpFactors.filter(::holds)

    /** Decides the two-step-verification nudge: a passkey holder already has it. */
    val hasStrongFactor: Boolean get() = passkeyRegistered || hasPin
}
