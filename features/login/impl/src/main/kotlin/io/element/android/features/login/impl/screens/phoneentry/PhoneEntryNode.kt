/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.login.impl.util.openLearnMorePage
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.matrix.api.auth.OAuthDetails

@ContributesNode(AppScope::class)
@AssistedInject
class PhoneEntryNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: PhoneEntryPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Params(
        val initialPhoneNumber: String?,
    ) : NodeInputs

    interface Callback : Plugin {
        fun navigateToOAuth(oAuthDetails: OAuthDetails)
        fun navigateToCountryPicker()
    }

    private val params = inputs<Params>()
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(params)

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val context = LocalContext.current
        PhoneEntryView(
            state = state,
            modifier = modifier,
            onOAuthDetails = callback::navigateToOAuth,
            onSelectCountry = callback::navigateToCountryPicker,
            onLearnMoreClick = { openLearnMorePage(context) },
        )
    }
}
