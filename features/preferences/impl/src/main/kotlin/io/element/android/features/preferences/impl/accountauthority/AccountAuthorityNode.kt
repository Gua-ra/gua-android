/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

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
import io.element.android.libraries.di.SessionScope

/**
 * GUA FORK: the account authority screen (ADM-009).
 *
 * No callback, and the one browser hop there is proves a factor rather than acting: the browser holds no
 * authority, ever (decision 6), so what the web step-up leaves behind is a note that this account proved its
 * passkey for one transition, and the record is still built, signed and submitted here. The other thing a web
 * session can do in this feature is start an approval, and that approval is granted on this screen rather than
 * in the page that asked for it.
 */
@ContributesNode(SessionScope::class)
@AssistedInject
class AccountAuthorityNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: AccountAuthorityPresenter,
) : Node(buildContext, plugins = plugins) {
    @Composable
    override fun View(modifier: Modifier) {
        val activity = requireNotNull(LocalActivity.current)
        val isDark = ElementTheme.isLightTheme.not()
        val state = presenter.present()
        AccountAuthorityView(
            state = state,
            onBackClick = ::navigateUp,
            // GUA FORK: the step-up URL is one-time and self-authenticating, exactly like a factor-enrollment
            // one, so it opens in an EPHEMERAL Custom Tab: it establishes its own login cookie, and a tab
            // sharing the browser's cookies is how a sheet ends up confirming for whichever account that
            // browser was already signed in as.
            onOpenWebStepUpUrl = { url ->
                activity.openUrlInChromeCustomTab(session = null, darkTheme = isDark, url = url, ephemeral = true)
            },
            modifier = modifier,
        )
    }
}
