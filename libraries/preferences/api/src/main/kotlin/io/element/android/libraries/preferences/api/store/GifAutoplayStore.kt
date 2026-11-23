/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.api.store

import kotlinx.coroutines.flow.StateFlow

/** The GIF autoplay switch in Media settings. It stays on this device and is never synced. */
interface GifAutoplayStore {
    /** On by default. Holds the stored value from the start, so the first frame drawn already follows it. */
    val playGifsAutomatically: StateFlow<Boolean>

    fun setPlayGifsAutomatically(enabled: Boolean)
}
