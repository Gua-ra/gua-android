/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.chatbackground.api.ChatBackgroundEntryPoint
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultChatBackgroundEntryPoint : ChatBackgroundEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: ChatBackgroundEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<ChatBackgroundNode>(buildContext, listOf(callback))
    }
}
