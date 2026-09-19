/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.grantauthority

import androidx.annotation.StringRes

/**
 * GUA FORK: giving the device that was just linked authority over the account (ADM-009 decision 5).
 *
 * This runs in exactly one direction, and only that one: this phone generated the QR and its user typed the
 * check code shown on the new device. In the other direction the code binds a channel rather than a peer, so
 * the key being signed over would be whatever came up that channel.
 */
enum class GrantAuthorityPhase {
    /** The offer, with the new device named. Declining leaves the link exactly as it was. */
    Prompt,

    /** The scoped step-up the grant's challenge is minted with. */
    StepUp,
    Submitting,
    Done,
}

data class GrantAuthorityState(
    val phase: GrantAuthorityPhase,
    /** The new device's own label, which is what the grant record and its notification will name. */
    val deviceLabel: String,
    val pin: String,
    @StringRes val errorMessage: Int?,
    val eventSink: (GrantAuthorityEvent) -> Unit,
) {
    val isWorking: Boolean = phase == GrantAuthorityPhase.Submitting

    val canSubmit: Boolean = pin.length == PIN_LENGTH && !isWorking

    companion object {
        const val PIN_LENGTH = 6
    }
}
