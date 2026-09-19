/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.test

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.features.findfriends.api.FindFriendsEntryPoint

/**
 * GUA FORK: test fake for [FindFriendsEntryPoint] — returns a no-op node and captures the supplied
 * callback so flows wiring Find friends can be exercised without the impl module.
 */
class FakeFindFriendsEntryPoint(
    private val nodeFactory: (BuildContext, List<Plugin>) -> Node,
) : FindFriendsEntryPoint {
    var callback: FindFriendsEntryPoint.Callback? = null
        private set

    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: FindFriendsEntryPoint.Callback,
    ): Node {
        this.callback = callback
        return nodeFactory(buildContext, listOf(callback))
    }
}
