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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

internal const val LIVE_WALLPAPER_FPS = 30
internal const val LIVE_WALLPAPER_FRAME_NANOS = 1_000_000_000L / LIVE_WALLPAPER_FPS

/**
 * Slack for a vsync that lands just short of a frame boundary, so 60, 90 and 120 Hz displays all draw every 33 ms, and a frame one 60 Hz
 * vsync late (50 ms, 1.5 times the budget) is not a slow frame.
 */
internal const val FRAME_TOLERANCE_NANOS = 4_000_000L

// The frame loop wakes this long before the next frame is due, so it skips the vsyncs in between but not the one it draws on.
private const val FRAME_WAKE_MARGIN_NANOS = 10_000_000L
private const val NANOS_PER_MILLI = 1_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val NO_TIME = Long.MIN_VALUE

private const val TIME_PERIOD_SECONDS = 1800.0
private const val PULSE_RISE_SECONDS = 0.25
private const val PULSE_DECAY_SECONDS = 0.9
private const val PULSE_END_SECONDS = 4.2

/** The shader's `u_time`. It starts at the still's time, so the first frame is the still, and wraps every 30 minutes to keep precision. */
internal fun liveWallpaperTime(stillTime: Float, elapsedSeconds: Double): Float {
    return ((stillTime + elapsedSeconds) % TIME_PERIOD_SECONDS).toFloat()
}

/** The shader's `u_pulse`, [secondsSinceStart] after a pulse started: the Lab's send envelope. */
internal fun sendPulse(secondsSinceStart: Double): Double {
    if (secondsSinceStart < 0 || secondsSinceStart > PULSE_END_SECONDS) return 0.0
    val rise = min(1.0, secondsSinceStart / PULSE_RISE_SECONDS)
    val decay = exp(-max(0.0, secondsSinceStart - PULSE_RISE_SECONDS) / PULSE_DECAY_SECONDS)
    return rise * rise * (3 - 2 * rise) * decay
}

/** The inverse of the rising edge: how long after its start the pulse reaches [value]. */
internal fun sendPulseRiseSeconds(value: Double): Double {
    return PULSE_RISE_SECONDS * (0.5 - sin(asin(1 - 2 * value) / 3))
}

internal fun liveWallpaperWakeDelayMillis(frameTimeNanos: Long, nowNanos: Long): Long {
    return max(0L, frameTimeNanos + LIVE_WALLPAPER_FRAME_NANOS - FRAME_WAKE_MARGIN_NANOS - nowNanos) / NANOS_PER_MILLI
}

/**
 * Plays the send pulse on each increase of the send count; the first count is only the baseline. Times are in the frame clock's
 * System.nanoTime base. A send during a pulse restarts it where the rising edge has the current value, so the shaders' rings never snap
 * back. The envelope runs in real time, so a frame drawn after a pause shows only what is left of it.
 */
internal class LiveWallpaperSendPulse(private var seenCount: Int, private var startNanos: Long = NO_TIME) {
    fun onSendCount(count: Int, nowNanos: Long) {
        if (count > seenCount) {
            val current = if (startNanos == NO_TIME) 0.0 else sendPulse((nowNanos - startNanos) / NANOS_PER_SECOND)
            startNanos = nowNanos - (sendPulseRiseSeconds(current) * NANOS_PER_SECOND).roundToLong()
        }
        seenCount = count
    }

    fun at(frameTimeNanos: Long): Float {
        if (startNanos == NO_TIME) return 0f
        return sendPulse((frameTimeNanos - startNanos) / NANOS_PER_SECOND).toFloat()
    }

    companion object {
        /** Keeps the pulse while a pushed screen takes the chat out of the composition, so a chat shown again soon plays the rest of it. */
        val Saver = Saver<LiveWallpaperSendPulse, LongArray>(
            save = { longArrayOf(it.seenCount.toLong(), it.startNanos) },
            restore = { LiveWallpaperSendPulse(seenCount = it[0].toInt(), startNanos = it[1]) },
        )
    }
}

/**
 * Advances only through [onFrame], at most 30 times a second, so a pause freezes the frame. Read [time], [pulse] and [tilt] only while
 * drawing, so a new frame redraws without recomposing.
 */
@Stable
internal class LiveWallpaperClock(private val stillTime: Float) {
    var time by mutableFloatStateOf(liveWallpaperTime(stillTime, 0.0))
        private set
    var pulse by mutableFloatStateOf(0f)
        private set
    var tilt by mutableStateOf(Offset.Zero)
        private set

    private var elapsedNanos = 0L
    private var lastFrameNanos = NO_TIME

    /** Keeps the frame and the tilt but drops the pulse, as a paused wallpaper shows none. */
    fun hold() {
        pulse = 0f
    }

    /** The time spent paused does not count. */
    fun resume() {
        lastFrameNanos = NO_TIME
    }

    /** Moves to [frameTimeNanos] if a 30 fps frame has passed since the last one drawn; returns whether it did. */
    fun onFrame(frameTimeNanos: Long, sends: LiveWallpaperSendPulse, tilt: Offset = Offset.Zero): Boolean {
        if (lastFrameNanos != NO_TIME) {
            val delta = frameTimeNanos - lastFrameNanos
            if (delta < LIVE_WALLPAPER_FRAME_NANOS - FRAME_TOLERANCE_NANOS) return false
            elapsedNanos += delta
        }
        lastFrameNanos = frameTimeNanos
        time = liveWallpaperTime(stillTime, elapsedNanos / NANOS_PER_SECOND)
        pulse = sends.at(frameTimeNanos)
        this.tilt = tilt
        return true
    }
}
