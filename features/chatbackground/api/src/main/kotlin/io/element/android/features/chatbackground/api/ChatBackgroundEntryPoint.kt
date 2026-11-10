/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.libraries.architecture.FeatureEntryPoint

interface ChatBackgroundEntryPoint : FeatureEntryPoint {
    /** [parentNode] must belong to a room: the screen edits that chat's own theme. */
    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
    ): Node

    /** The screen for the personal default theme, which only needs [parentNode] to belong to a session. */
    fun createDefaultNode(
        parentNode: Node,
        buildContext: BuildContext,
    ): Node
}
