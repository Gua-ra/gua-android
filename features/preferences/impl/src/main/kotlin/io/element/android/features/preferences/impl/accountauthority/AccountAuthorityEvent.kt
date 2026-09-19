/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

/**
 * GUA FORK: UI actions for the account authority screen (ADM-009).
 *
 * There is no event that carries a phone code, and there is no event that submits an adoption without
 * [ConfirmArtifactStored] having been sent first: decision 7 makes the recovery artifact the only way back
 * from a lost device, so the confirmation is part of the path rather than a dialog beside it.
 */
sealed interface AccountAuthorityEvent {
    /** Re-read the chain, the device set and the live approvals. */
    data object Refresh : AccountAuthorityEvent

    /** Generate this device's authority keys and show the recovery artifact once. */
    data object StartAdoption : AccountAuthorityEvent

    /** The user ticked or unticked "I have stored this". */
    data class ConfirmArtifactStored(val confirmed: Boolean) : AccountAuthorityEvent

    /** Leave the artifact screen for the step-up. Refused while the confirmation is not ticked. */
    data object ContinueFromArtifact : AccountAuthorityEvent

    /** The user edited the PIN field. */
    data class PinChanged(val pin: String) : AccountAuthorityEvent

    /** Submit whatever the step-up was asked for: the adoption, or an opposition. */
    data object Submit : AccountAuthorityEvent

    /** Object to the pending transition on this account. */
    data object Oppose : AccountAuthorityEvent

    /** Sign one live browser approval as this account's authority device. */
    data class Approve(val approvalId: String) : AccountAuthorityEvent

    /** Leave an in-progress step and go back to the overview. */
    data object Cancel : AccountAuthorityEvent

    /** The success message has been shown; clear it. */
    data object ClearSuccess : AccountAuthorityEvent
}
