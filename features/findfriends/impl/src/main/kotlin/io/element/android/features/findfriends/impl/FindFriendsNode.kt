/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.findfriends.api.FindFriendsEntryPoint
import io.element.android.libraries.di.SessionScope

/**
 * GUA FORK: Appyx node for the Find friends screen. Android counterpart of iOS'
 * `FindFriendsScreenCoordinator`. Wires [FindFriendsPresenter] to [FindFriendsView] and forwards
 * navigation results to the [FindFriendsEntryPoint.Callback] supplied by the surrounding flow.
 */
@ContributesNode(SessionScope::class)
@AssistedInject
class FindFriendsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: FindFriendsPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    private val callback = plugins<FindFriendsEntryPoint.Callback>().first()
    private val presenter = presenterFactory.create(callback)

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        FindFriendsView(
            state = state,
            onBackClick = ::navigateUp,
            modifier = modifier,
        )
    }
}
