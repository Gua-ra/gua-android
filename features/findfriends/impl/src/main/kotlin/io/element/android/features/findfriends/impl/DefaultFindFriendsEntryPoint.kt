/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.findfriends.api.FindFriendsEntryPoint
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultFindFriendsEntryPoint : FindFriendsEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: FindFriendsEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<FindFriendsNode>(buildContext, listOf(callback))
    }
}
