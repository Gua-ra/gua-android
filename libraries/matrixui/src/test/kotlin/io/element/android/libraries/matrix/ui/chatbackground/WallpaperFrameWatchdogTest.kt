/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val MS = 1_000_000L
private const val VSYNC_60_HZ = 16_666_667L

class WallpaperFrameWatchdogTest {
    @Test
    fun `frames on budget never trip it`() {
        assertThat(WallpaperFrameWatchdog().drawFrames(List(300) { 33 * MS })).isFalse()
    }

    @Test
    fun `slow frames trip it as soon as the window holds 10 of them`() {
        val watchdog = WallpaperFrameWatchdog()
        assertThat(watchdog.drawFrames(listOf(33 * MS) + List(WATCHDOG_MINIMUM_FRAMES - 1) { 60 * MS })).isFalse()
        assertThat(watchdog.onFrame(33 * MS + WATCHDOG_MINIMUM_FRAMES * 60 * MS)).isTrue()
    }

    @Test
    fun `the first interval of a run is not in the window`() {
        val compile = 400 * MS
        val janky = listOf(60 * MS) + List(8) { 33 * MS }
        assertThat(WallpaperFrameWatchdog().drawFrames(listOf(compile) + janky + List(60) { 33 * MS })).isFalse()
        assertThat(WallpaperFrameWatchdog().drawFrames(listOf(33 * MS, compile) + janky)).isTrue()
        assertThat(WallpaperFrameWatchdog().drawFrames(List(WATCHDOG_MINIMUM_FRAMES + 1) { 60 * MS })).isTrue()
    }

    @Test
    fun `the 90th percentile of 10 frames ignores one slow frame but not two`() {
        assertThat(WallpaperFrameWatchdog().drawFrames(listOf(33 * MS) + List(9) { 33 * MS } + 500 * MS)).isFalse()
        assertThat(WallpaperFrameWatchdog().drawFrames(listOf(33 * MS) + List(8) { 33 * MS } + List(2) { 60 * MS })).isTrue()
    }

    @Test
    fun `a single long stall does not trip it`() {
        listOf(500 * MS, 1_500 * MS, 3_000 * MS).forEach { stall ->
            val frames = List(60) { 33 * MS } + stall + List(60) { 33 * MS }
            assertThat(WallpaperFrameWatchdog().drawFrames(frames)).isFalse()
        }
    }

    @Test
    fun `frames older than 2 seconds leave the window`() {
        val frames = List(WATCHDOG_MINIMUM_FRAMES) { 60 * MS } + 2_500 * MS + List(WATCHDOG_MINIMUM_FRAMES - 1) { 33 * MS }
        assertThat(WallpaperFrameWatchdog().drawFrames(frames)).isFalse()
    }

    @Test
    fun `it trips when more than a tenth of the frames in the window are slow`() {
        val rarelySlow = List(20) { index -> if (index == 0) VSYNC_60_HZ * 4 else VSYNC_60_HZ * 2 }
        assertThat(WallpaperFrameWatchdog().drawFrames(List(10) { rarelySlow }.flatten())).isFalse()
        val oftenSlow = List(4) { index -> if (index == 0) VSYNC_60_HZ * 4 else VSYNC_60_HZ * 2 }
        assertThat(WallpaperFrameWatchdog().drawFrames(List(50) { oftenSlow }.flatten())).isTrue()
    }

    @Test
    fun `frames one 60 Hz vsync late do not trip it, whatever the panel's exact period`() {
        listOf(VSYNC_60_HZ, 16_683_350L, 16_700_000L).forEach { vsync ->
            assertThat(WallpaperFrameWatchdog().drawFrames(List(150) { vsync * 3 })).isFalse()
        }
        assertThat(WallpaperFrameWatchdog().drawFrames(List(150) { 50_040_000L })).isFalse()
    }

    @Test
    fun `frames two 60 Hz vsyncs late trip it`() {
        assertThat(WallpaperFrameWatchdog().drawFrames(List(150) { VSYNC_60_HZ * 4 })).isTrue()
    }

    @Test
    fun `frames of 54 ms do not trip it and frames over 54 ms do`() {
        assertThat(WallpaperFrameWatchdog().drawFrames(List(150) { 53_999_999L })).isFalse()
        assertThat(WallpaperFrameWatchdog().drawFrames(List(150) { 54_000_000L })).isFalse()
        assertThat(WallpaperFrameWatchdog().drawFrames(List(150) { 54_000_001L })).isTrue()
        assertThat(WallpaperFrameWatchdog().drawFrames(List(WATCHDOG_MINIMUM_FRAMES + 1) { 54_000_001L })).isTrue()
    }

    @Test
    fun `once tripped it stays tripped`() {
        val watchdog = WallpaperFrameWatchdog()
        watchdog.drawFrames(List(60) { 100 * MS })
        assertThat(watchdog.hasTripped).isTrue()
        assertThat(watchdog.drawFrames(List(300) { 33 * MS }, startNanos = 6_000 * MS)).isTrue()
    }

    /** Draws a first frame at [startNanos], then one frame after each duration; returns the last verdict. */
    private fun WallpaperFrameWatchdog.drawFrames(durations: List<Long>, startNanos: Long = 0): Boolean {
        var now = startNanos
        var tripped = onFrame(now)
        durations.forEach { duration ->
            now += duration
            tripped = onFrame(now)
        }
        return tripped
    }
}
