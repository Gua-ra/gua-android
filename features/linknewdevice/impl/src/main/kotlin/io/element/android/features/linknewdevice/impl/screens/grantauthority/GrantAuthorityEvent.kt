/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.grantauthority

/** GUA FORK: UI actions for the grant offer that follows a device link (ADM-009 decision 5). */
sealed interface GrantAuthorityEvent {
    /** Accept the offer and go to the fingerprint comparison. */
    data object Grant : GrantAuthorityEvent

    /** The user said the eight characters match what the other phone shows, or unsaid it. */
    data class ConfirmFingerprint(val confirmed: Boolean) : GrantAuthorityEvent

    /** Leave the comparison for the step-up. Refused while the comparison was not confirmed. */
    data object ContinueFromCompare : GrantAuthorityEvent

    data class PinChanged(val pin: String) : GrantAuthorityEvent

    /** Sign and submit the grant. */
    data object Submit : GrantAuthorityEvent

    /**
     * Decline. The new device stays signed in with no authority, which is a complete and ordinary state:
     * account access and account authority are different things (ADM-009 decision 6 says so for browsers,
     * and it is just as true for a second phone).
     */
    data object Skip : GrantAuthorityEvent
}
