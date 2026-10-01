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

/** The provider for a private tab, which shares no cookies with the browser, or null to use the ordinary shared tab. */
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

/** A failing support check counts as unsupported. */
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
