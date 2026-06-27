/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

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

@ContributesNode(SessionScope::class)
@AssistedInject
class ChangePhoneNumberNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ChangePhoneNumberPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        /** Open the shared country picker for the new-number field. */
        fun navigateToCountryPicker()

        /** Open the existing 2SV PIN-setup flow (no PIN is set, so the change cannot proceed). */
        fun navigateToPinSetup()
    }

    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(
        navigateToCountryPicker = callback::navigateToCountryPicker,
        navigateToPinSetup = callback::navigateToPinSetup,
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ChangePhoneNumberView(
            state = state,
            onBackClick = ::navigateUp,
            onFinished = ::navigateUp,
            modifier = modifier,
        )
    }
}
