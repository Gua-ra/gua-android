/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.chatbackground.api.ChatBackgroundEntryPoint
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.RoomScope

@ContributesNode(RoomScope::class)
@AssistedInject
class ChatBackgroundNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: ChatBackgroundPresenter,
) : Node(buildContext, plugins = plugins) {
    private val callback: ChatBackgroundEntryPoint.Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ChatBackgroundView(
            state = state,
            onBackClick = ::navigateUp,
            onDone = callback::onDone,
            modifier = modifier,
        )
    }
}
