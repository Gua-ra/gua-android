/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorBuilder
import org.robolectric.shadows.SensorEventBuilder
import kotlin.math.exp

private const val MS = 1_000_000L

@OptIn(ExperimentalTestApi::class)
class WallpaperTiltSensorTest : RobolectricTest() {
    @Test
    fun `the gyroscope is listened to only while the wallpaper animates, a pause holds the tilt and a still brings it back to rest`() =
        runAndroidComposeUiTest<ComponentActivity> {
            val sensorManager = activity!!.getSystemService(SensorManager::class.java)
            shadowOf(sensorManager).addSensor(SensorBuilder.newBuilder().setType(Sensor.TYPE_GYROSCOPE).build())
            var motion by mutableStateOf(WallpaperMotion.Animating)
            var tilt: WallpaperTiltSensor? = null
            setContent {
                val remembered = rememberWallpaperTiltSensor()
                tilt = remembered
                if (remembered != null) {
                    WallpaperTiltEffect(sensor = remembered, motion = motion)
                }
            }
            val sensor = runOnIdle { tilt!! }
            runOnIdle { assertThat(shadowOf(sensorManager).hasListener(sensor)).isTrue() }
            // The right edge turns toward the viewer, who is then to the screen's right.
            sensorManager.sendRotationRate(timestampNanos = 0, rateY = -0.8f)
            sensorManager.sendRotationRate(timestampNanos = 20 * MS, rateY = -0.8f)
            val turned = runOnIdle { sensor.value }
            assertThat(turned.x).isGreaterThan(0f)

            motion = WallpaperMotion.Paused
            runOnIdle {
                assertThat(shadowOf(sensorManager).hasListener(sensor)).isFalse()
                assertThat(sensor.value).isEqualTo(turned)
            }
            motion = WallpaperMotion.Animating
            runOnIdle { assertThat(shadowOf(sensorManager).hasListener(sensor)).isTrue() }

            motion = WallpaperMotion.Still
            runOnIdle {
                assertThat(shadowOf(sensorManager).hasListener(sensor)).isFalse()
                assertThat(sensor.value).isEqualTo(Offset.Zero)
            }
        }

    @Test
    fun `a still condition that clears during the ease to rest lets the tilt ease on from where the picture stopped`() =
        runAndroidComposeUiTest<ComponentActivity> {
            val sensorManager = activity!!.getSystemService(SensorManager::class.java)
            shadowOf(sensorManager).addSensor(SensorBuilder.newBuilder().setType(Sensor.TYPE_GYROSCOPE).build())
            var motion by mutableStateOf(WallpaperMotion.Animating)
            var tilt: WallpaperTiltSensor? = null
            setContent {
                val remembered = rememberWallpaperTiltSensor()
                tilt = remembered
                if (remembered != null) {
                    WallpaperTiltEffect(sensor = remembered, motion = motion)
                }
            }
            val sensor = runOnIdle { tilt!! }
            // Turned for 0.3 s, so one smoothing step toward rest is well past the dead band.
            repeat(16) { sensorManager.sendRotationRate(timestampNanos = it * 20 * MS, rateY = -0.8f) }
            val turned = runOnIdle { sensor.value }
            assertThat(turned.x).isGreaterThan(0.3f)

            mainClock.autoAdvance = false
            motion = WallpaperMotion.Still
            waitForIdle()
            mainClock.advanceTimeBy(150)
            motion = WallpaperMotion.Animating
            waitForIdle()
            mainClock.advanceTimeByFrame()
            val partway = runOnIdle { sensor.value }
            assertThat(partway.x).isIn(Range.open(0f, turned.x))
            assertThat(shadowOf(sensorManager).hasListener(sensor)).isTrue()

            // The first sample after the start only sets the clock, so nothing moves.
            sensorManager.sendRotationRate(timestampNanos = 1000 * MS, rateY = 0f)
            runOnIdle { assertThat(sensor.value).isEqualTo(partway) }
            // The viewer is back on the normal, so the tilt eases toward rest by one 0.06 s smoothing step.
            sensorManager.sendRotationRate(timestampNanos = 1020 * MS, rateY = 0f)
            val eased = runOnIdle { sensor.value }
            assertThat(eased.x).isWithin(1e-5f).of(partway.x * exp(-0.02 / 0.06).toFloat())
            assertThat(eased.y).isWithin(1e-5f).of(0f)
        }

    @Test
    fun `a device without a gyroscope has no tilt`() = runAndroidComposeUiTest<ComponentActivity> {
        var hasComposed = false
        var tilt: WallpaperTiltSensor? = null
        setContent {
            tilt = rememberWallpaperTiltSensor()
            hasComposed = true
        }
        runOnIdle {
            assertThat(hasComposed).isTrue()
            assertThat(tilt).isNull()
        }
    }

    private fun SensorManager.sendRotationRate(timestampNanos: Long, rateY: Float) {
        val event = SensorEventBuilder.newBuilder(getDefaultSensor(Sensor.TYPE_GYROSCOPE)!!, floatArrayOf(0f, rateY, 0f))
            .setTimestamp(timestampNanos)
            .build()
        shadowOf(this).sendSensorEventToListeners(event)
    }
}
