/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback

@ContributesNode(AppScope::class)
@AssistedInject
class CountryPickerNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: CountryPickerPresenter,
    private val selectedCountryStore: SelectedCountryStore,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        /** Selection is published via [SelectedCountryStore]; the flow just needs to pop. */
        fun onDone()
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        CountryPickerView(
            state = state,
            onSelectCountry = { country ->
                selectedCountryStore.select(country)
                callback.onDone()
            },
            modifier = modifier,
        )
    }
}
