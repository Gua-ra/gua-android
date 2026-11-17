/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.os.Build
import androidx.compose.ui.graphics.BlendMode

/** How the grain tile is blended over a wallpaper. */
internal data class WallpaperGrainStyle(val blendMode: BlendMode, val alpha: Float)

// Soft light keeps the grain at about 1 level from the dark variants up to mid-tones. Below API 29 it would draw as SrcOver,
// a visible veil, so Overlay stands in there: about 1 to 2 levels at mid-tones, fading towards black and white.
internal fun wallpaperGrainStyle(sdkInt: Int = Build.VERSION.SDK_INT) = if (sdkInt >= Build.VERSION_CODES.Q) {
    WallpaperGrainStyle(BlendMode.Softlight, alpha = 0.08f)
} else {
    WallpaperGrainStyle(BlendMode.Overlay, alpha = 0.04f)
}
