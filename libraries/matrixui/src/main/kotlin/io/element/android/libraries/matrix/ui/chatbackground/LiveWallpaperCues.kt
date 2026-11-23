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
 * A chat background drawn without cues shows its still, as tiles and pickers do.
 *
 * @property isCovered a sheet, a dialog or the message menu covers the wallpaper, or the top bar and the composer leave the chat
 * no room, so it pauses.
 * @property sendCount each increase plays the send pulse.
 */
@Immutable
data class LiveWallpaperCues(
    val isCovered: Boolean = false,
    val sendCount: Int = 0,
)

/** True while an overlay such as the media viewer hides the screen but leaves it resumed, so live wallpapers under it pause. */
val LocalLiveWallpaperHidden = compositionLocalOf { false }

/** Lets tests read the cues a chat background was drawn with; accessibility services ignore it. */
val LiveWallpaperCuesKey = SemanticsPropertyKey<LiveWallpaperCues>("LiveWallpaperCues")

internal var SemanticsPropertyReceiver.liveWallpaperCues by LiveWallpaperCuesKey
