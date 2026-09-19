/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

/**
 * GUA FORK: UI actions sent to [FindFriendsPresenter]. Android counterpart of iOS
 * `FindFriendsScreenViewAction`.
 */
sealed interface FindFriendsEvents {
    /** Request the contacts permission (from the needs-permission state). */
    data object RequestPermission : FindFriendsEvents

    /** Open the system settings (from the permission-denied state). */
    data object OpenSettings : FindFriendsEvents

    /** Re-run discovery. */
    data object Retry : FindFriendsEvents

    /** Start (or open an existing) chat with the selected contact. */
    data class StartChat(val contact: DiscoveredContact) : FindFriendsEvents

    /** Open the selected contact's profile. */
    data class OpenProfile(val contact: DiscoveredContact) : FindFriendsEvents
}
