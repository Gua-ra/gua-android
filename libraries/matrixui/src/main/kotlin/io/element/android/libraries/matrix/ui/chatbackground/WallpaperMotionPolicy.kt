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
 * @property isCovered a sheet, a dialog or the message menu covers the chat, or the top bar and the composer leave it no room.
 * @property animationsEnabled the system animator duration scale is not 0.
 * @property isPowerSaveMode Battery Saver is on.
 * @property thermalStatus a `PowerManager.THERMAL_STATUS_*` value, `THERMAL_STATUS_NONE` below Android 10.
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

    private fun asksForStill(): Boolean {
        val isThermalThrottled = thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        return !animationsEnabled || isPowerSaveMode || isThermalThrottled || isOverFrameBudget
    }
}
