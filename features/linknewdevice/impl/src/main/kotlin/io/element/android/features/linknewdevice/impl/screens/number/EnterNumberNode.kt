/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.number

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope

/**
 * GUA FORK: no wrong-code destination.
 *
 * The screen cannot tell a wrong code from a right one: the comparison happens inside the ceremony, and its
 * verdict arrives on the step flow as a failed ceremony, which the flow node maps to the mismatch screen. The
 * callback that used to exist here was only ever reachable from a check that always said yes.
 */
interface EnterNumberNavigator

@ContributesNode(SessionScope::class)
@AssistedInject
class EnterNumberNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: EnterNumberPresenter.Factory,
) : Node(buildContext, plugins = plugins), EnterNumberNavigator {
    private val presenter = presenterFactory.create(this)

    interface Callback : Plugin {
        fun navigateBack()
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        EnterNumberView(
            state = state,
            modifier = modifier,
            onBackClick = callback::navigateBack,
        )
    }

}
