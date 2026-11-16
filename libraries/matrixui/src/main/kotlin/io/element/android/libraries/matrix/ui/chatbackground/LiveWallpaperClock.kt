/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Live wallpapers draw at most this many frames a second. */
internal const val LIVE_WALLPAPER_FPS = 30
internal const val LIVE_WALLPAPER_FRAME_NANOS = 1_000_000_000L / LIVE_WALLPAPER_FPS

/**
 * How far a vsync may land from a frame boundary and still count as on it. A vsync just short of the budget still draws, so 60, 90 and
 * 120 Hz displays all draw every 33 ms, and a frame one 60 Hz vsync late (50 ms, exactly 1.5 times the budget) is not a slow frame.
 */
internal const val FRAME_TOLERANCE_NANOS = 4_000_000L

// The frame loop sleeps until this long before the next frame is due, so it skips the vsyncs in between but not the one it draws on.
private const val FRAME_WAKE_MARGIN_NANOS = 10_000_000L
private const val NANOS_PER_MILLI = 1_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val NO_TIME = Long.MIN_VALUE

private const val TIME_PERIOD_SECONDS = 1800.0
private const val PULSE_RISE_SECONDS = 0.25
private const val PULSE_DECAY_SECONDS = 0.9
private const val PULSE_END_SECONDS = 4.2

/**
 * The shader's `u_time`, [elapsedSeconds] after the wallpaper appeared. It starts at the still's time, so the first frame is the still,
 * and wraps every 30 minutes so float precision never degrades.
 */
internal fun liveWallpaperTime(stillTime: Float, elapsedSeconds: Double): Float {
    return ((stillTime + elapsedSeconds) % TIME_PERIOD_SECONDS).toFloat()
}

/** The shader's `u_pulse`, [secondsSinceSend] after the user sent a message: the Lab's "Send a message" envelope. */
internal fun sendPulse(secondsSinceSend: Double): Float {
    if (secondsSinceSend < 0 || secondsSinceSend > PULSE_END_SECONDS) return 0f
    val rise = min(1.0, secondsSinceSend / PULSE_RISE_SECONDS)
    val decay = exp(-max(0.0, secondsSinceSend - PULSE_RISE_SECONDS) / PULSE_DECAY_SECONDS)
    return (rise * rise * (3 - 2 * rise) * decay).toFloat()
}

/** How long the frame loop can sleep after drawing the frame of the vsync at [frameTimeNanos], when it is [nowNanos]. */
internal fun liveWallpaperWakeDelayMillis(frameTimeNanos: Long, nowNanos: Long): Long {
    return max(0L, frameTimeNanos + LIVE_WALLPAPER_FRAME_NANOS - FRAME_WAKE_MARGIN_NANOS - nowNanos) / NANOS_PER_MILLI
}

/**
 * The user's sends, as the wallpaper's send pulse. The count it starts with is only where counting starts; each increase after that
 * starts the envelope at the time it is seen, in the time base of frame times. The envelope runs in real time, so a frame drawn after a
 * pause shows only what is left of it, and nothing from 4.2 s after the send.
 */
internal class LiveWallpaperSendPulse(private var seenCount: Int) {
    private var startNanos = NO_TIME

    fun onSendCount(count: Int, nowNanos: Long) {
        if (count > seenCount) {
            startNanos = nowNanos
        }
        seenCount = count
    }

    /** The pulse in the frame of the vsync at [frameTimeNanos]. */
    fun at(frameTimeNanos: Long): Float {
        if (startNanos == NO_TIME) return 0f
        return sendPulse((frameTimeNanos - startNanos) / NANOS_PER_SECOND)
    }
}

/**
 * A live wallpaper's own time. It advances only through [onFrame], at most 30 times a second, so a pause freezes the frame and
 * [resume] continues from it. Read [time] and [pulse] only while drawing, so a new frame redraws without recomposing.
 */
@Stable
internal class LiveWallpaperClock(private val stillTime: Float) {
    var time by mutableFloatStateOf(liveWallpaperTime(stillTime, 0.0))
        private set
    var pulse by mutableFloatStateOf(0f)
        private set

    private var elapsedNanos = 0L
    private var lastFrameNanos = NO_TIME

    /** The frame stays as it is but shows no pulse, as a paused wallpaper does. */
    fun hold() {
        pulse = 0f
    }

    /** The next frame starts from the current one, so the time the wallpaper was paused does not count. */
    fun resume() {
        lastFrameNanos = NO_TIME
    }

    /**
     * Moves to the vsync at [frameTimeNanos] if a 30 fps frame has passed since the last frame drawn, with the pulse of [sends] at that
     * vsync; returns whether it did.
     */
    fun onFrame(frameTimeNanos: Long, sends: LiveWallpaperSendPulse): Boolean {
        if (lastFrameNanos != NO_TIME) {
            val delta = frameTimeNanos - lastFrameNanos
            if (delta < LIVE_WALLPAPER_FRAME_NANOS - FRAME_TOLERANCE_NANOS) return false
            elapsedNanos += delta
        }
        lastFrameNanos = frameTimeNanos
        time = liveWallpaperTime(stillTime, elapsedNanos / NANOS_PER_SECOND)
        pulse = sends.at(frameTimeNanos)
        return true
    }
}
