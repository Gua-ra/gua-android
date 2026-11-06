/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.test

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.chatbackground.api.ChatBackgroundEntryPoint
import io.element.android.tests.testutils.lambda.lambdaError

class FakeChatBackgroundEntryPoint : ChatBackgroundEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: ChatBackgroundEntryPoint.Callback,
    ): Node {
        lambdaError()
    }
}
