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
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class DefaultChatThemeNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ChatThemePresenter.Factory,
    target: PersonalDefaultChatThemeTarget,
) : Node(buildContext, plugins = plugins) {
    private val presenter = presenterFactory.create(target)

    @Composable
    override fun View(modifier: Modifier) {
        ChatThemeView(
            state = presenter.present(),
            onBackClick = ::navigateUp,
            modifier = modifier,
        )
    }
}
