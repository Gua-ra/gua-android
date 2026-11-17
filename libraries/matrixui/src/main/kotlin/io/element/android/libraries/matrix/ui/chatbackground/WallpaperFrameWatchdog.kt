/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import kotlin.math.ceil

private const val NO_FRAME = Long.MIN_VALUE
private const val PERCENTILE = 0.9
private const val WINDOW_NANOS = 2_000_000_000L

/** 1.5 times the 30 fps budget plus [FRAME_TOLERANCE_NANOS]: 54 ms. */
private const val LIMIT_NANOS = 1_000_000_000L * 3 / (2 * LIVE_WALLPAPER_FPS) + FRAME_TOLERANCE_NANOS

/** With fewer frames than this in the window, one stray frame would decide the percentile. */
internal const val WATCHDOG_MINIMUM_FRAMES = 10

/**
 * Trips once the 90th percentile of the intervals between the wallpaper's drawn frames, over a rolling 2 s window that holds at least
 * [WATCHDOG_MINIMUM_FRAMES] of them, is over 54 ms. Each animation run gets a new instance, so a pause restarts the window and is never
 * read as one slow frame.
 */
internal class WallpaperFrameWatchdog {
    private val frames = ArrayDeque<Frame>()
    private var lastFrameNanos = NO_FRAME

    // When a run starts with the shader's first draw, the render thread compiles its GPU program and can hold up the next frame, so the
    // window starts at the run's second drawn frame and that compile is never read as a slow frame.
    private var framesBeforeWindow = 2

    var hasTripped = false
        private set

    /** Records a frame drawn at [frameTimeNanos]; returns whether the watchdog has tripped. */
    fun onFrame(frameTimeNanos: Long): Boolean {
        if (hasTripped) return true
        val previousFrameNanos = lastFrameNanos
        lastFrameNanos = frameTimeNanos
        if (framesBeforeWindow > 0) {
            framesBeforeWindow--
            return false
        }
        frames.addLast(Frame(endNanos = frameTimeNanos, durationNanos = frameTimeNanos - previousFrameNanos))
        while (frames.first().endNanos <= frameTimeNanos - WINDOW_NANOS) {
            frames.removeFirst()
        }
        hasTripped = frames.size >= WATCHDOG_MINIMUM_FRAMES && percentile90() > LIMIT_NANOS
        return hasTripped
    }

    private fun percentile90(): Long {
        val sorted = frames.map { it.durationNanos }.sorted()
        return sorted[ceil(sorted.size * PERCENTILE).toInt() - 1]
    }

    private data class Frame(val endNanos: Long, val durationNanos: Long)
}
