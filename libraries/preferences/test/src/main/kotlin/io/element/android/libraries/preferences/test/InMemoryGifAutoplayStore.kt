/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.test

import io.element.android.libraries.preferences.api.store.GifAutoplayStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryGifAutoplayStore(
    playGifsAutomatically: Boolean = true,
) : GifAutoplayStore {
    private val enabled = MutableStateFlow(playGifsAutomatically)
    override val playGifsAutomatically: StateFlow<Boolean> = enabled.asStateFlow()

    override fun setPlayGifsAutomatically(enabled: Boolean) {
        this.enabled.value = enabled
    }
}
