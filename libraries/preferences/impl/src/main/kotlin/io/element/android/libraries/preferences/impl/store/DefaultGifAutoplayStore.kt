/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.impl.store

import android.content.Context
import android.content.SharedPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.preferences.api.store.GifAutoplayStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// SharedPreferences rather than DataStore: the first read is synchronous, so no screen draws a frame with the default.
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultGifAutoplayStore(
    @ApplicationContext context: Context,
) : GifAutoplayStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val enabled = MutableStateFlow(preferences.getBoolean(KEY_PLAY_GIFS_AUTOMATICALLY, true))
    override val playGifsAutomatically: StateFlow<Boolean> = enabled.asStateFlow()

    override fun setPlayGifsAutomatically(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PLAY_GIFS_AUTOMATICALLY, enabled).apply()
        this.enabled.value = enabled
    }

    private companion object {
        const val PREFERENCES_NAME = "gua_media_settings"
        const val KEY_PLAY_GIFS_AUTOMATICALLY = "play_gifs_automatically"
    }
}
