/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.os.Build
import androidx.compose.ui.graphics.BlendMode

internal data class WallpaperGrainStyle(val blendMode: BlendMode, val alpha: Float)

// Below API 29 Softlight draws as SrcOver, a visible veil. Overlay at half the alpha comes close at mid-tones.
internal fun wallpaperGrainStyle(sdkInt: Int = Build.VERSION.SDK_INT) = if (sdkInt >= Build.VERSION_CODES.Q) {
    WallpaperGrainStyle(BlendMode.Softlight, alpha = 0.08f)
} else {
    WallpaperGrainStyle(BlendMode.Overlay, alpha = 0.04f)
}
