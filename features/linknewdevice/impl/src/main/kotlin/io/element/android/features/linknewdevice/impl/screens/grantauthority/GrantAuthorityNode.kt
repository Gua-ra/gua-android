/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.grantauthority

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate

/** GUA FORK: the grant offer that follows a link in the one permitted direction (ADM-009 decision 5). */
@ContributesNode(SessionScope::class)
@AssistedInject
class GrantAuthorityNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: GrantAuthorityPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val candidate: AuthorityCandidate) : NodeInputs

    interface Callback : Plugin {
        /** The user granted or declined. Either way the link flow is finished. */
        fun onDone()
    }

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(
        candidate = inputs.candidate,
        onDone = callback::onDone,
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        GrantAuthorityView(
            state = state,
            modifier = modifier,
        )
    }
}
