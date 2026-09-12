/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
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

        /**
         * Open the existing 2SV PIN-setup flow. Offered as one of the two ways out of the step-up
         * block, alongside registering a passkey; never as the only one when a passkey is possible.
         */
        fun navigateToPinSetup()
    }

    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(
        navigateToCountryPicker = callback::navigateToCountryPicker,
        navigateToPinSetup = callback::navigateToPinSetup,
    )

    @Composable
    override fun View(modifier: Modifier) {
        val activity = requireNotNull(LocalActivity.current)
        val isDark = ElementTheme.isLightTheme.not()
        val state = presenter.present()
        ChangePhoneNumberView(
            state = state,
            onBackClick = ::navigateUp,
            onFinish = ::navigateUp,
            // GUA FORK: the enrollUrl is self-authenticating, so it opens in a Chrome Custom Tab for
            // the user to complete WebAuthn registration, exactly as the 2SV screen does.
            onOpenPasskeyEnrollUrl = { url ->
                activity.openUrlInChromeCustomTab(session = null, darkTheme = isDark, url = url)
            },
            modifier = modifier,
        )
    }
}
