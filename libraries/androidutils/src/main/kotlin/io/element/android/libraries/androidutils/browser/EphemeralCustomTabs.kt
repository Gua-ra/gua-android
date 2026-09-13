/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.browser

import android.content.Context
import androidx.browser.customtabs.CustomTabsClient
import timber.log.Timber

/**
 * GUA FORK: the Custom Tabs provider to use for a private (ephemeral) tab, or null to open the
 * ordinary shared tab instead.
 *
 * A normal Custom Tab shares the browser's cookie jar, so a sign-in page opened in it can pick up
 * whichever account last signed in on that browser and carry on as that account. An ephemeral tab
 * starts with no cookies and forgets them when it closes, which is what iOS gets from an ephemeral
 * web authentication session. Browsers that do not support it keep today's behaviour.
 *
 * The provider is the user's default browser when it supports Custom Tabs. `ignoreDefault = true`
 * with an empty candidate list would never return anything, so the default is not ignored here.
 */
fun ephemeralCustomTabsProvider(context: Context): String? {
    return try {
        chooseEphemeralProvider(
            defaultProvider = CustomTabsClient.getPackageName(context, emptyList()),
            supportsEphemeralBrowsing = { CustomTabsClient.isEphemeralBrowsingSupported(context, it) },
        )
    } catch (e: RuntimeException) {
        Timber.w(e, "Could not resolve a Custom Tabs provider for a private tab")
        null
    }
}

/**
 * GUA FORK: the decision behind [ephemeralCustomTabsProvider], without the package manager: the
 * [defaultProvider] when it reports ephemeral browsing support, else null. A failing support check
 * counts as unsupported.
 */
fun chooseEphemeralProvider(
    defaultProvider: String?,
    supportsEphemeralBrowsing: (String) -> Boolean,
): String? {
    if (defaultProvider.isNullOrEmpty()) return null
    val supported = try {
        supportsEphemeralBrowsing(defaultProvider)
    } catch (e: RuntimeException) {
        Timber.w(e, "Ephemeral browsing support check failed")
        false
    }
    return defaultProvider.takeIf { supported }
}
