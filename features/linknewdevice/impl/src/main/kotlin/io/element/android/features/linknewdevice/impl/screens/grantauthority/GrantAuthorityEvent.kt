/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.grantauthority

sealed interface GrantAuthorityEvent {
    data object Grant : GrantAuthorityEvent

    data class ConfirmFingerprint(val confirmed: Boolean) : GrantAuthorityEvent

    data object ContinueFromCompare : GrantAuthorityEvent

    data class PinChanged(val pin: String) : GrantAuthorityEvent

    data object Submit : GrantAuthorityEvent

    data object Skip : GrantAuthorityEvent
}
