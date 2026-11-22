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
    /** Edits the chat's own theme; [parentNode] must belong to a room. */
    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
    ): Node

    /** Edits the personal default theme; [parentNode] only needs to belong to a session. */
    fun createDefaultNode(
        parentNode: Node,
        buildContext: BuildContext,
    ): Node
}
