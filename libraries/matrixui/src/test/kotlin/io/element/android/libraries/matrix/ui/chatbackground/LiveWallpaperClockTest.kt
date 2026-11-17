/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.runtime.saveable.SaverScope
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.exp

private const val FRAME = LIVE_WALLPAPER_FRAME_NANOS
private const val VSYNC_60_HZ = 16_666_667L
private const val MS = 1_000_000L

class LiveWallpaperClockTest {
    private val noSends = LiveWallpaperSendPulse(seenCount = 0)

    @Test
    fun `u_time starts at the still's time and wraps every 30 minutes`() {
        assertThat(liveWallpaperTime(stillTime = 24f, elapsedSeconds = 0.0)).isEqualTo(24f)
        assertThat(liveWallpaperTime(stillTime = 24f, elapsedSeconds = 6.5)).isEqualTo(30.5f)
        assertThat(liveWallpaperTime(stillTime = 48f, elapsedSeconds = 1752.0)).isEqualTo(0f)
        assertThat(liveWallpaperTime(stillTime = 48f, elapsedSeconds = 1762.25)).isEqualTo(10.25f)
        assertThat(liveWallpaperTime(stillTime = 0f, elapsedSeconds = 3600.0 * 24 + 1)).isEqualTo(1f)
    }

    @Test
    fun `the send pulse follows the Lab envelope`() {
        assertThat(sendPulse(0.0)).isEqualTo(0f)
        assertThat(sendPulse(0.125)).isWithin(1e-6f).of(0.5f)
        assertThat(sendPulse(0.25)).isWithin(1e-6f).of(1f)
        assertThat(sendPulse(1.15)).isWithin(1e-6f).of(exp(-1.0).toFloat())
        assertThat(sendPulse(4.2)).isWithin(1e-6f).of(exp(-3.95 / 0.9).toFloat())
    }

    @Test
    fun `the send pulse is 0 before a send and from 4,2 seconds after it`() {
        assertThat(sendPulse(-0.1)).isEqualTo(0f)
        assertThat(sendPulse(4.2001)).isEqualTo(0f)
        assertThat(sendPulse(60.0)).isEqualTo(0f)
    }

    @Test
    fun `the first frame is the still`() {
        val clock = LiveWallpaperClock(stillTime = 14f)
        assertThat(clock.time).isEqualTo(14f)
        assertThat(clock.onFrame(5_000_000_000L, noSends)).isTrue()
        assertThat(clock.time).isEqualTo(14f)
        assertThat(clock.pulse).isEqualTo(0f)
    }

    @Test
    fun `frames hold 30 fps on a 60 Hz display`() {
        val clock = LiveWallpaperClock(stillTime = 0f)
        val drawn = (0..12).map { vsync -> clock.onFrame(vsync * VSYNC_60_HZ, noSends) }
        assertThat(drawn).isEqualTo(List(13) { vsync -> vsync % 2 == 0 })
        assertThat(clock.time).isWithin(1e-4f).of(12 * VSYNC_60_HZ / 1e9f)
    }

    @Test
    fun `frames hold 30 fps on 90 and 120 Hz displays`() {
        listOf(11_111_111L to 3, 8_333_333L to 4).forEach { (vsync, vsyncsPerFrame) ->
            val clock = LiveWallpaperClock(stillTime = 0f)
            val drawn = (0..24).map { index -> clock.onFrame(index * vsync, noSends) }
            assertThat(drawn).isEqualTo(List(25) { index -> index % vsyncsPerFrame == 0 })
        }
    }

    @Test
    fun `a pause stops the time and resuming continues from where it paused`() {
        val clock = LiveWallpaperClock(stillTime = 24f)
        clock.onFrame(0, noSends)
        clock.onFrame(FRAME, noSends)
        clock.onFrame(2 * FRAME, noSends)
        val pausedAt = clock.time
        clock.hold()
        clock.resume()
        clock.onFrame(60_000_000_000L, noSends)
        assertThat(clock.time).isEqualTo(pausedAt)
        clock.onFrame(60_000_000_000L + FRAME, noSends)
        assertThat(clock.time).isWithin(1e-4f).of(24f + 3 * FRAME / 1e9f)
    }

    @Test
    fun `a new send count plays the pulse from when it is seen`() {
        val sends = LiveWallpaperSendPulse(seenCount = 3)
        val clock = LiveWallpaperClock(stillTime = 0f)
        clock.onFrame(0, sends)
        sends.onSendCount(3, nowNanos = 10 * MS)
        clock.onFrame(FRAME, sends)
        assertThat(clock.pulse).isEqualTo(0f)
        sends.onSendCount(4, nowNanos = FRAME)
        clock.onFrame(2 * FRAME, sends)
        assertThat(clock.pulse).isWithin(1e-6f).of(sendPulse(FRAME / 1e9))
        clock.onFrame(FRAME + 250 * MS, sends)
        assertThat(clock.pulse).isWithin(1e-6f).of(1f)
        clock.onFrame(FRAME + 4_300 * MS, sends)
        assertThat(clock.pulse).isEqualTo(0f)
    }

    @Test
    fun `the count the wallpaper starts with plays nothing`() {
        val sends = LiveWallpaperSendPulse(seenCount = 7)
        sends.onSendCount(7, nowNanos = 0)
        assertThat(sends.at(125 * MS)).isEqualTo(0f)
    }

    @Test
    fun `a lower count, from a timeline that started again, becomes the new baseline`() {
        val sends = LiveWallpaperSendPulse(seenCount = 5)
        sends.onSendCount(0, nowNanos = 0)
        assertThat(sends.at(125 * MS)).isEqualTo(0f)
        sends.onSendCount(1, nowNanos = 1_000 * MS)
        assertThat(sends.at(1_125 * MS)).isWithin(1e-6f).of(0.5f)
    }

    @Test
    fun `a paused frame shows no pulse`() {
        val sends = LiveWallpaperSendPulse(seenCount = 0)
        val clock = LiveWallpaperClock(stillTime = 0f)
        clock.onFrame(0, sends)
        sends.onSendCount(1, nowNanos = 0)
        clock.onFrame(125 * MS, sends)
        assertThat(clock.pulse).isGreaterThan(0f)
        clock.hold()
        assertThat(clock.pulse).isEqualTo(0f)
    }

    @Test
    fun `resuming after a pause plays only what is left of the pulse`() {
        val sends = LiveWallpaperSendPulse(seenCount = 0)
        val clock = LiveWallpaperClock(stillTime = 0f)
        clock.onFrame(0, sends)
        sends.onSendCount(1, nowNanos = 0)
        clock.onFrame(125 * MS, sends)
        clock.hold()
        clock.resume()
        clock.onFrame(1_150 * MS, sends)
        assertThat(clock.pulse).isWithin(1e-6f).of(exp(-1.0).toFloat())
    }

    @Test
    fun `resuming long after a send plays no pulse`() {
        val sends = LiveWallpaperSendPulse(seenCount = 0)
        val clock = LiveWallpaperClock(stillTime = 0f)
        clock.onFrame(0, sends)
        sends.onSendCount(1, nowNanos = 0)
        clock.onFrame(125 * MS, sends)
        clock.hold()
        clock.resume()
        clock.onFrame(30_000 * MS, sends)
        assertThat(clock.pulse).isEqualTo(0f)
    }

    @Test
    fun `a send seen while the wallpaper shows its still plays the rest of the pulse once it animates`() {
        val sends = LiveWallpaperSendPulse(seenCount = 0)
        sends.onSendCount(1, nowNanos = 1_000 * MS)
        val clock = LiveWallpaperClock(stillTime = 0f)
        clock.onFrame(1_250 * MS, sends)
        assertThat(clock.pulse).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `a send keeps its pulse while the screen leaves the composition`() {
        val sends = LiveWallpaperSendPulse(seenCount = 2)
        sends.onSendCount(3, nowNanos = 0)
        val saved = with(LiveWallpaperSendPulse.Saver) { SaverScope { true }.save(sends) }
        val restored = LiveWallpaperSendPulse.Saver.restore(saved!!)!!
        restored.onSendCount(3, nowNanos = 1_000 * MS)
        assertThat(restored.at(1_150 * MS)).isWithin(1e-6f).of(exp(-1.0).toFloat())
        assertThat(restored.at(4_300 * MS)).isEqualTo(0f)
        restored.onSendCount(4, nowNanos = 5_000 * MS)
        assertThat(restored.at(5_250 * MS)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `the frame loop sleeps until shortly before the next frame is due`() {
        assertThat(liveWallpaperWakeDelayMillis(frameTimeNanos = 0, nowNanos = 2 * MS)).isEqualTo(21)
        assertThat(liveWallpaperWakeDelayMillis(frameTimeNanos = 1_000 * MS, nowNanos = 1_000 * MS)).isEqualTo(23)
        assertThat(liveWallpaperWakeDelayMillis(frameTimeNanos = 0, nowNanos = 30 * MS)).isEqualTo(0)
    }
}
