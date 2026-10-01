/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.grantauthority

import androidx.annotation.StringRes

enum class GrantAuthorityPhase {
    Prompt,

    Compare,

    StepUp,
    Submitting,
    Done,
}

data class GrantAuthorityState(
    val phase: GrantAuthorityPhase,
    val deviceLabel: String,
    val fingerprint: String,
    val fingerprintConfirmed: Boolean,
    val pin: String,
    @StringRes val errorMessage: Int?,
    val eventSink: (GrantAuthorityEvent) -> Unit,
) {
    val isWorking: Boolean = phase == GrantAuthorityPhase.Submitting

    val canContinueFromCompare: Boolean = fingerprintConfirmed

    val canSubmit: Boolean = fingerprintConfirmed && pin.length == PIN_LENGTH && !isWorking

    companion object {
        const val PIN_LENGTH = 6
    }
}
