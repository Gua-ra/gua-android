/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.After
import org.junit.Test
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalTestApi::class)
class WallpaperMotionObserversTest : RobolectricTest() {
    private val powerManager = ApplicationProvider.getApplicationContext<Context>().getSystemService(PowerManager::class.java)

    @After
    fun tearDown() {
        setAnimatorDurationScale(1f)
        shadowOf(powerManager).setIsPowerSaveMode(false)
        shadowOf(powerManager).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
        LiveWallpaperSession.reset()
    }

    @Test
    fun `a resumed screen with nothing over it animates`() = runAndroidComposeUiTest<ComponentActivity> {
        var motion: WallpaperMotion? = null
        setContent { motion = rememberWallpaperMotion(isCovered = false) }
        runOnIdle { assertThat(motion).isEqualTo(WallpaperMotion.Animating) }
    }

    @Test
    fun `an overlay that hides the screen pauses its wallpaper, and hiding the overlay resumes it`() = runAndroidComposeUiTest<ComponentActivity> {
        var isHidden by mutableStateOf(false)
        var motion: WallpaperMotion? = null
        setContent {
            CompositionLocalProvider(LocalLiveWallpaperHidden provides isHidden) {
                motion = rememberWallpaperMotion(isCovered = false)
            }
        }
        runOnIdle { assertThat(motion).isEqualTo(WallpaperMotion.Animating) }
        isHidden = true
        runOnIdle { assertThat(motion).isEqualTo(WallpaperMotion.Paused) }
        isHidden = false
        runOnIdle { assertThat(motion).isEqualTo(WallpaperMotion.Animating) }
    }

    @Test
    fun `a thermal status of moderate shows the still from the next change`() = runAndroidComposeUiTest<ComponentActivity> {
        var motion: WallpaperMotion? = null
        var canAnimate: Boolean? = null
        setContent {
            motion = rememberWallpaperMotion(isCovered = false)
            canAnimate = rememberLiveWallpaperCanAnimate(ChatArtwork.GlassDrops)
        }
        runOnIdle { assertThat(canAnimate).isTrue() }
        shadowOf(powerManager).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
        runOnIdle {
            assertThat(motion).isEqualTo(WallpaperMotion.Still)
            assertThat(canAnimate).isFalse()
        }
    }

    @Test
    fun `battery saver shows the still`() = runAndroidComposeUiTest<ComponentActivity> {
        shadowOf(powerManager).setIsPowerSaveMode(true)
        var canAnimate: Boolean? = null
        setContent { canAnimate = rememberLiveWallpaperCanAnimate(ChatArtwork.GlassDrops) }
        runOnIdle { assertThat(canAnimate).isFalse() }
    }

    @Test
    fun `remove animations shows the still`() = runAndroidComposeUiTest<ComponentActivity> {
        setAnimatorDurationScale(0f)
        var canAnimate: Boolean? = null
        setContent { canAnimate = rememberLiveWallpaperCanAnimate(ChatArtwork.GlassDrops) }
        runOnIdle { assertThat(canAnimate).isFalse() }
    }

    @Test
    fun `a covered screen pauses but can still animate`() = runAndroidComposeUiTest<ComponentActivity> {
        var motion: WallpaperMotion? = null
        var canAnimate: Boolean? = null
        setContent {
            motion = rememberWallpaperMotion(isCovered = true)
            canAnimate = rememberLiveWallpaperCanAnimate(ChatArtwork.GlassDrops)
        }
        runOnIdle {
            assertThat(motion).isEqualTo(WallpaperMotion.Paused)
            assertThat(canAnimate).isTrue()
        }
    }

    @Test
    fun `an artwork whose shader failed cannot animate, from the moment it fails`() = runAndroidComposeUiTest<ComponentActivity> {
        var glassDrops: Boolean? = null
        var silk: Boolean? = null
        var stillArtwork: Boolean? = null
        setContent {
            glassDrops = rememberLiveWallpaperCanAnimate(ChatArtwork.GlassDrops)
            silk = rememberLiveWallpaperCanAnimate(ChatArtwork.Silk)
            stillArtwork = rememberLiveWallpaperCanAnimate(ChatArtwork.PalmCoast)
        }
        runOnIdle {
            assertThat(glassDrops).isTrue()
            assertThat(silk).isTrue()
            assertThat(stillArtwork).isFalse()
        }
        assertThat(LiveWallpaperSession.reportUnavailable(ChatArtwork.GlassDrops)).isTrue()
        assertThat(LiveWallpaperSession.reportUnavailable(ChatArtwork.GlassDrops)).isFalse()
        runOnIdle {
            assertThat(glassDrops).isFalse()
            assertThat(silk).isTrue()
        }
    }

    // ValueAnimator.setDurationScale is a hidden API that Robolectric implements; the system sets it from the developer options.
    private fun setAnimatorDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }
}
