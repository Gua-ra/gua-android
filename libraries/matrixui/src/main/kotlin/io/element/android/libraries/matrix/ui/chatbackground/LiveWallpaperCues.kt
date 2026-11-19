/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * What a screen tells its live artwork. A chat background drawn without cues shows the still, as tiles and pickers do.
 *
 * @property isCovered a sheet, a dialog or the message menu covers the wallpaper, so it pauses.
 * @property sendCount the user's sends; each increase plays the send pulse.
 */
@Immutable
data class LiveWallpaperCues(
    val isCovered: Boolean = false,
    val sendCount: Int = 0,
)

/**
 * True while something hides the whole screen without taking it out of the resumed lifecycle, such as the media viewer, which the
 * messages flow shows as an overlay over the chat. A live wallpaper under it pauses, as when its screen is not on screen.
 */
val LocalLiveWallpaperHidden = compositionLocalOf { false }

/** The cues a chat background was drawn with. Not read by accessibility services; tests use it to check what a screen passes. */
val LiveWallpaperCuesKey = SemanticsPropertyKey<LiveWallpaperCues>("LiveWallpaperCues")

internal var SemanticsPropertyReceiver.liveWallpaperCues by LiveWallpaperCuesKey
