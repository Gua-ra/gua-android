/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.di.SessionScope

/**
 * GUA FORK: the account authority screen (ADM-009).
 *
 * No callback and no browser hop: every step of this feature is native by rule, because the browser holds no
 * authority, ever (decision 6). The one thing a web session can do in this feature is start an approval, and
 * that approval is granted here rather than there.
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
        val state = presenter.present()
        AccountAuthorityView(
            state = state,
            onBackClick = ::navigateUp,
            modifier = modifier,
        )
    }
}
