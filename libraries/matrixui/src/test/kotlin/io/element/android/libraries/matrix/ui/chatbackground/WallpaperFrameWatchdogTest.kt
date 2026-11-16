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
    fun `slow frames for 2 seconds trip it`() {
        val watchdog = WallpaperFrameWatchdog()
        assertThat(watchdog.drawFrames(List(30) { 60 * MS })).isFalse()
        assertThat(watchdog.drawFrames(List(10) { 60 * MS }, startNanos = 30 * 60 * MS)).isTrue()
    }

    @Test
    fun `hitches shorter than 2 seconds do not trip it`() {
        val hitch = List(15) { 60 * MS } + List(60) { 33 * MS }
        assertThat(WallpaperFrameWatchdog().drawFrames(hitch + hitch + hitch)).isFalse()
    }

    @Test
    fun `it trips when more than a tenth of the frames stay over 1,5 times the budget`() {
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
    fun `frames over 1,5 times the budget by more than a vsync's jitter trip it`() {
        assertThat(WallpaperFrameWatchdog().drawFrames(List(150) { 53_999_999L })).isFalse()
        assertThat(WallpaperFrameWatchdog().drawFrames(List(150) { 54_000_000L })).isTrue()
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
