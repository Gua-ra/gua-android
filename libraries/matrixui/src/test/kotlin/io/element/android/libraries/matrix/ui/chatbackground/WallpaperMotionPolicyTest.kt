/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.os.PowerManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WallpaperMotionPolicyTest {
    private val animating = WallpaperMotionPolicy(
        isOnScreen = true,
        isCovered = false,
        animationsEnabled = true,
        isPowerSaveMode = false,
        thermalStatus = PowerManager.THERMAL_STATUS_NONE,
        isOverFrameBudget = false,
    )

    @Test
    fun `a chat on screen with nothing over it animates`() {
        assertThat(animating.motion).isEqualTo(WallpaperMotion.Animating)
    }

    @Test
    fun `a chat that is not on screen pauses`() {
        assertThat(animating.copy(isOnScreen = false).motion).isEqualTo(WallpaperMotion.Paused)
    }

    @Test
    fun `a sheet or the message menu over the chat pauses it`() {
        assertThat(animating.copy(isCovered = true).motion).isEqualTo(WallpaperMotion.Paused)
    }

    @Test
    fun `remove animations shows the still`() {
        assertThat(animating.copy(animationsEnabled = false).motion).isEqualTo(WallpaperMotion.Still)
    }

    @Test
    fun `battery saver shows the still`() {
        assertThat(animating.copy(isPowerSaveMode = true).motion).isEqualTo(WallpaperMotion.Still)
    }

    @Test
    fun `a thermal status of moderate or worse shows the still`() {
        assertThat(animating.copy(thermalStatus = PowerManager.THERMAL_STATUS_LIGHT).motion).isEqualTo(WallpaperMotion.Animating)
        listOf(
            PowerManager.THERMAL_STATUS_MODERATE,
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN,
        ).forEach { status ->
            assertThat(animating.copy(thermalStatus = status).motion).isEqualTo(WallpaperMotion.Still)
        }
    }

    @Test
    fun `a tripped frame watchdog shows the still`() {
        assertThat(animating.copy(isOverFrameBudget = true).motion).isEqualTo(WallpaperMotion.Still)
    }

    @Test
    fun `the still wins over a pause`() {
        val policy = animating.copy(isOnScreen = false, isCovered = true, isPowerSaveMode = true)
        assertThat(policy.motion).isEqualTo(WallpaperMotion.Still)
    }

    @Test
    fun `the still shows until the shader has drawn, and whenever the policy asks for it`() {
        assertThat(liveArtShowsStill(WallpaperMotion.Animating, hasDrawn = false)).isTrue()
        assertThat(liveArtShowsStill(WallpaperMotion.Paused, hasDrawn = false)).isTrue()
        assertThat(liveArtShowsStill(WallpaperMotion.Animating, hasDrawn = true)).isFalse()
        assertThat(liveArtShowsStill(WallpaperMotion.Paused, hasDrawn = true)).isFalse()
        assertThat(liveArtShowsStill(WallpaperMotion.Still, hasDrawn = true)).isTrue()
    }
}
