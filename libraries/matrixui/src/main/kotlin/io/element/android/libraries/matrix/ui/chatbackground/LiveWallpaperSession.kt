/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import io.element.android.libraries.chatbackground.api.ChatArtwork

/** State shared by every live wallpaper until the app restarts. */
object LiveWallpaperSession {
    /** Once a live wallpaper has been too slow, every live wallpaper shows its still. */
    internal var isOverFrameBudget by mutableStateOf(false)

    // Written from the thread that compiles the shader, read in composition.
    private val unavailableArtworks = mutableStateSetOf<ChatArtwork>()

    internal fun isAvailable(artwork: ChatArtwork): Boolean = artwork !in unavailableArtworks

    /** Makes [artwork] show its still from now on; returns false if it was already reported. */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun reportUnavailable(artwork: ChatArtwork): Boolean = unavailableArtworks.add(artwork)

    @VisibleForTesting
    fun reset() {
        isOverFrameBudget = false
        unavailableArtworks.clear()
    }
}
