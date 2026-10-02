/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

sealed interface TwoStepVerificationEvent {
    /**
     * Start enrollment of the first PIN (no existing PIN): fetches the authenticated web-ceremony URL,
     * exactly as [SetUpPasskey] does.
     */
    data object StartSetup : TwoStepVerificationEvent

    /** Start the OTP-protected change flow (existing PIN). */
    data object StartChange : TwoStepVerificationEvent

    data class CodeChanged(val code: String) : TwoStepVerificationEvent

    /** The local digits in the confirm-number field changed; triggers auto-detect + national masking. */
    data class PhoneChanged(val value: String) : TwoStepVerificationEvent

    data object SelectCountry : TwoStepVerificationEvent

    data object Continue : TwoStepVerificationEvent

    data object CancelEntry : TwoStepVerificationEvent

    /** The success message has been shown; clear it. */
    data object ClearSuccess : TwoStepVerificationEvent

    /** Start passkey enrollment: fetches the authenticated web-ceremony URL to open at the IdP. */
    data object SetUpPasskey : TwoStepVerificationEvent

    /** The enrollment URL has been opened (in a Chrome Custom Tab); clear it. */
    data object ClearFactorEnrollUrl : TwoStepVerificationEvent
}
