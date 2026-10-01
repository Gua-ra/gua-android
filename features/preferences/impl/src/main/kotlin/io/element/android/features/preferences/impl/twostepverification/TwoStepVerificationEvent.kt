/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

sealed interface TwoStepVerificationEvent {
    data object StartSetup : TwoStepVerificationEvent

    data object StartChange : TwoStepVerificationEvent

    data class CodeChanged(val code: String) : TwoStepVerificationEvent

    data class PhoneChanged(val value: String) : TwoStepVerificationEvent

    data object SelectCountry : TwoStepVerificationEvent

    data object Continue : TwoStepVerificationEvent

    data object CancelEntry : TwoStepVerificationEvent

    data object ClearSuccess : TwoStepVerificationEvent

    data object SetUpPasskey : TwoStepVerificationEvent

    data object ClearFactorEnrollUrl : TwoStepVerificationEvent
}
