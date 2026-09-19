/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.collections.immutable.ImmutableList

/**
 * GUA FORK: immutable UI state for the Find friends screen. Android counterpart of iOS
 * `FindFriendsScreenViewState` / `FindFriendsScreenPhase`.
 */
data class FindFriendsState(
    val phase: FindFriendsPhase,
    val contacts: ImmutableList<DiscoveredContact>,
    /** User id of the contact whose chat is currently being opened (drives a per-row spinner). */
    val startingChatUserId: UserId?,
    val eventSink: (FindFriendsEvents) -> Unit,
)

enum class FindFriendsPhase {
    /** Reading contacts and looking them up. */
    Loading,

    /** Contacts permission has not been granted yet — show a CTA to request it. */
    NeedsPermission,

    /** Contacts permission was permanently denied — show a CTA to open system settings. */
    PermissionDenied,

    /** Discovery ran but none of the user's contacts are on Gua yet. */
    Empty,

    /** Discovery succeeded with at least one match. */
    Loaded,

    /** Discovery failed (e.g. the identity-service was unreachable). */
    Error,
}
