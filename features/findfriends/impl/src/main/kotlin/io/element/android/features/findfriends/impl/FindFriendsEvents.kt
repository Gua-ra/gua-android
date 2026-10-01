/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

sealed interface FindFriendsEvents {
    data object RequestPermission : FindFriendsEvents

    data object OpenSettings : FindFriendsEvents

    data object Retry : FindFriendsEvents

    data class StartChat(val contact: DiscoveredContact) : FindFriendsEvents

    data class OpenProfile(val contact: DiscoveredContact) : FindFriendsEvents
}
