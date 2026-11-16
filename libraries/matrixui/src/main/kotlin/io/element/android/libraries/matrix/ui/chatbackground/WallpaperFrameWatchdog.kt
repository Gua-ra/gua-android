/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.ceil

private const val NO_FRAME = Long.MIN_VALUE
private const val PERCENTILE = 0.9

/**
 * Trips once the 90th-percentile time between the wallpaper's frames, over the last [percentileWindowNanos], has stayed above
 * 1.5 times the 30 fps budget for [sustainNanos]. A device too slow for the shader stays above it, while a short hitch elsewhere in the
 * app does not. Each animation run gets a new instance, so a pause never reads as one slow frame.
 */
internal class WallpaperFrameWatchdog(
    budgetNanos: Long = LIVE_WALLPAPER_FRAME_NANOS,
    private val percentileWindowNanos: Long = 1_000_000_000L,
    private val sustainNanos: Long = 2_000_000_000L,
) {
    private val limitNanos = budgetNanos * 3 / 2 + FRAME_TOLERANCE_NANOS
    private val frames = ArrayDeque<Frame>()
    private var lastFrameNanos = NO_FRAME
    private var overBudgetSinceNanos = NO_FRAME

    var hasTripped = false
        private set

    /** Records a frame drawn at [frameTimeNanos]; returns whether the watchdog has tripped. */
    fun onFrame(frameTimeNanos: Long): Boolean {
        if (hasTripped) return true
        val previousFrameNanos = lastFrameNanos
        lastFrameNanos = frameTimeNanos
        if (previousFrameNanos == NO_FRAME) return false
        frames.addLast(Frame(endNanos = frameTimeNanos, durationNanos = frameTimeNanos - previousFrameNanos))
        while (frames.first().endNanos <= frameTimeNanos - percentileWindowNanos) {
            frames.removeFirst()
        }
        if (percentile90() > limitNanos) {
            if (overBudgetSinceNanos == NO_FRAME) overBudgetSinceNanos = frameTimeNanos
            hasTripped = frameTimeNanos - overBudgetSinceNanos >= sustainNanos
        } else {
            overBudgetSinceNanos = NO_FRAME
        }
        return hasTripped
    }

    private fun percentile90(): Long {
        val sorted = frames.map { it.durationNanos }.sorted()
        return sorted[ceil(sorted.size * PERCENTILE).toInt() - 1]
    }

    private data class Frame(val endNanos: Long, val durationNanos: Long)
}

/** Once a live wallpaper has been too slow, every live wallpaper shows its still until the app restarts. */
internal object LiveWallpaperSession {
    var isOverFrameBudget by mutableStateOf(false)
}
