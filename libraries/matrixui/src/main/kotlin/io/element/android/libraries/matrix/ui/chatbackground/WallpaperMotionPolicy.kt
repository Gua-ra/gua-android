/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.os.PowerManager

/** How a live artwork shows. */
enum class WallpaperMotion {
    /** The shader runs. */
    Animating,

    /** The last frame stays and the wallpaper's time stops; it continues from there. */
    Paused,

    /** The still image, with no pulse. */
    Still,
}

/**
 * Whether a live artwork animates. The platform observers fill in the conditions; anything that asks for the still wins over a pause.
 *
 * @property isOnScreen the screen is resumed and nothing hides it: not in the background, not under another screen or an overlay such as
 * the media viewer, and the display is on.
 * @property isCovered a sheet, a dialog or the message menu covers the chat.
 * @property animationsEnabled the system animator duration scale is not 0 ("Remove animations" is off).
 * @property isPowerSaveMode Battery Saver is on.
 * @property thermalStatus a `PowerManager.THERMAL_STATUS_*` value; `THERMAL_STATUS_NONE` below Android 10.
 * @property isOverFrameBudget the frame-time watchdog has tripped in this session.
 */
data class WallpaperMotionPolicy(
    val isOnScreen: Boolean,
    val isCovered: Boolean,
    val animationsEnabled: Boolean,
    val isPowerSaveMode: Boolean,
    val thermalStatus: Int,
    val isOverFrameBudget: Boolean,
) {
    val motion: WallpaperMotion
        get() = when {
            asksForStill() -> WallpaperMotion.Still
            !isOnScreen || isCovered -> WallpaperMotion.Paused
            else -> WallpaperMotion.Animating
        }

    val animates: Boolean
        get() = motion == WallpaperMotion.Animating

    private fun asksForStill(): Boolean {
        val isThermalThrottled = thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        return !animationsEnabled || isPowerSaveMode || isThermalThrottled || isOverFrameBudget
    }
}
