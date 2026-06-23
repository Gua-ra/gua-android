/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

/**
 * GUA FORK: entry point for the "Find friends" contact-discovery screen — lets the user see which of
 * their phone contacts are already on Gua and start a chat with them. Android counterpart of iOS'
 * `FindFriendsScreenCoordinator`, surfaced from the new-chat / "+" flow.
 */
interface FindFriendsEntryPoint : FeatureEntryPoint {
    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        /** A direct chat with the selected contact is ready; open it. */
        fun onStartChat(roomId: RoomId)

        /** The user tapped a contact's avatar — open their profile. */
        fun onOpenProfile(userId: UserId)
    }
}
