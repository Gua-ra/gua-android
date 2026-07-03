/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * GUA FORK: sample states for previews + screenshot tests. Covers every Find friends phase, mirroring
 * iOS' `FindFriendsScreenPhase`.
 */
open class FindFriendsStateProvider : PreviewParameterProvider<FindFriendsState> {
    override val values: Sequence<FindFriendsState>
        get() = sequenceOf(
            aFindFriendsState(phase = FindFriendsPhase.Loaded, contacts = aDiscoveredContactList()),
            aFindFriendsState(phase = FindFriendsPhase.Empty),
            aFindFriendsState(phase = FindFriendsPhase.NeedsPermission),
            aFindFriendsState(phase = FindFriendsPhase.PermissionDenied),
            aFindFriendsState(phase = FindFriendsPhase.Loading),
            aFindFriendsState(phase = FindFriendsPhase.Error),
            aFindFriendsState(
                phase = FindFriendsPhase.Loaded,
                contacts = aDiscoveredContactList(),
                startingChatUserId = UserId("@alice:gua.global"),
            ),
        )
}

internal fun aFindFriendsState(
    phase: FindFriendsPhase = FindFriendsPhase.Loaded,
    contacts: List<DiscoveredContact> = aDiscoveredContactList(),
    startingChatUserId: UserId? = null,
) = FindFriendsState(
    phase = phase,
    contacts = contacts.toImmutableList(),
    startingChatUserId = startingChatUserId,
    eventSink = {},
)

internal fun aDiscoveredContactList(): List<DiscoveredContact> = persistentListOf(
    DiscoveredContact(
        localName = "Alice Martins",
        userId = UserId("@alice:gua.global"),
        handle = "@alice",
        avatarUrl = null,
    ),
    DiscoveredContact(
        localName = "Bruno Costa",
        userId = UserId("@bruno:gua.global"),
        handle = "@bruno",
        avatarUrl = null,
    ),
    DiscoveredContact(
        localName = "Carla Dias",
        userId = UserId("@carla:gua.global"),
        handle = "@carla",
        avatarUrl = null,
    ),
)
