/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

/**
 * GUA FORK: UI actions for the two-step-verification (account PIN) screen. Android counterpart of iOS
 * `TwoStepVerificationScreenViewAction`.
 */
sealed interface TwoStepVerificationEvent {
    /** Start the setup flow (no existing PIN). */
    data object StartSetup : TwoStepVerificationEvent

    /** Start the OTP-protected change flow (existing PIN). */
    data object StartChange : TwoStepVerificationEvent

    /** The user edited the 6-digit code field. */
    data class CodeChanged(val code: String) : TwoStepVerificationEvent

    /** The user edited the phone field (change flow). */
    data class PhoneChanged(val phone: String) : TwoStepVerificationEvent

    /** The user tapped the primary "Continue" button. */
    data object Continue : TwoStepVerificationEvent

    /** The user cancelled the in-progress flow and returned to the overview. */
    data object CancelEntry : TwoStepVerificationEvent

    /** The success message has been shown; clear it. */
    data object ClearSuccess : TwoStepVerificationEvent
}
