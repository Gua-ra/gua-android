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

enum class TwoStepVerificationPhase {
    Loading,

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
    val code: String,
    val selectedCountry: Country,
    val localPhoneNumber: String,
    @StringRes val errorMessage: Int?,
    val showSuccess: Boolean,
    val factorEnrollUrl: String?,
    val eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    val isWorking: Boolean = phase == TwoStepVerificationPhase.Submitting

    val hasPin: Boolean? = factors?.hasPin

    val passkeyRegistered: Boolean? = factors?.passkeyRegistered

    /** A passkey alone turns two-step verification on. */
    val twoStepVerificationOn: Boolean? = factors?.hasStrongFactor

    val localDigits: String get() = localPhoneNumber.filter { it.isDigit() }

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
