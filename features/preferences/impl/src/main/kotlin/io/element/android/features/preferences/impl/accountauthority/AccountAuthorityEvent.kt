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
 * There is no event that carries a phone code, and there is no event that submits an adoption or a recovery
 * without [ConfirmArtifactStored] having been sent first: decision 7 makes the recovery artifact the only way
 * back from a lost device, so the confirmation is part of the path rather than a dialog beside it. A grant is
 * the same shape with [ConfirmFingerprint] in that place.
 */
sealed interface AccountAuthorityEvent {
    /** Re-read the chain, the device set, the candidates and the live approvals. */
    data object Refresh : AccountAuthorityEvent

    /** Generate this device's authority keys and show the recovery artifact once. */
    data object StartAdoption : AccountAuthorityEvent

    /** The user ticked or unticked "I have stored this". */
    data class ConfirmArtifactStored(val confirmed: Boolean) : AccountAuthorityEvent

    /** Leave the artifact screen for the step-up. Refused while the confirmation is not ticked. */
    data object ContinueFromArtifact : AccountAuthorityEvent

    /** Start a recovery: ask for the artifact the owner kept. */
    data object StartRecovery : AccountAuthorityEvent

    /** The user edited the artifact they are typing back. */
    data class RecoveryArtifactChanged(val artifact: String) : AccountAuthorityEvent

    /**
     * Validate the artifact locally and mint the pair the recovery will install.
     *
     * Malformed material stops here. It never crosses the wire, so a client that submitted it anyway would
     * spend a challenge and a step-up to be told the signature did not verify.
     */
    data object ContinueFromRecoveryEntry : AccountAuthorityEvent

    /** Offer this device's own key so another device can add it. */
    data object OfferThisDevice : AccountAuthorityEvent

    /** Start granting one candidate: show its fingerprint to compare. */
    data class SelectCandidate(val deviceKeyB64Url: String) : AccountAuthorityEvent

    /** The user said the eight characters match what the other phone shows, or unsaid it. */
    data class ConfirmFingerprint(val confirmed: Boolean) : AccountAuthorityEvent

    /** Leave the comparison for the step-up. Refused while the comparison was not confirmed. */
    data object ContinueFromCompare : AccountAuthorityEvent

    /**
     * Start removing a device. [deviceKeyB64Url] may be this device's own key, which is a different
     * transition: it takes effect at once, because a device removing its own authority reduces what an
     * attacker holding it could do.
     */
    data class StartRevocation(val deviceKeyB64Url: String) : AccountAuthorityEvent

    /** The user edited the PIN field. */
    data class PinChanged(val pin: String) : AccountAuthorityEvent

    /** Submit whatever the step-up was asked for. */
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
