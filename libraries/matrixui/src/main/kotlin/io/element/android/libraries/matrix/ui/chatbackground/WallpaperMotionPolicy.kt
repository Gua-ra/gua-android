/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.os.PowerManager

enum class WallpaperMotion {
    /** The shader runs and the artwork tilts. */
    Animating,

    /** The frame and the tilt hold, and the wallpaper's time stops. */
    Paused,

    /** The still image at rest, with no pulse. */
    Still,
}

/**
 * Anything that asks for the still wins over a pause.
 *
 * @property isOnScreen the screen is resumed and no other screen or overlay hides it.
 * @property thermalStatus a `PowerManager.THERMAL_STATUS_*` value.
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
