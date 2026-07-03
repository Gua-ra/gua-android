/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.modifiers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import io.element.android.libraries.androidutils.system.areAnimationsEnabled
import timber.log.Timber

// The maximum tilt magnitude (in radians) we map to the full -1..1 range. Roughly 35 degrees of
// roll/pitch reaches the extremes; beyond that we clamp so the effect stays subtle and stable.
private const val MAX_TILT_RADIANS = 0.6f

// Low-pass easing factor for the smoothed value. Mirrors the iOS DeviceTiltMotion analogue:
// `current += (target - current) * 0.15f` on each sensor sample.
private const val TILT_EASING = 0.15f

/**
 * Returns a smoothed device-tilt [Offset] where `x` is roll and `y` is pitch, each roughly in the
 * range -1..1. This is the Android analogue of the iOS welcome screen's DeviceTiltMotion, used to
 * drive a tasteful parallax/highlight on the Gua logo.
 *
 * Sensor selection prefers [Sensor.TYPE_GAME_ROTATION_VECTOR] (drift-free, no magnetometer) and
 * falls back to [Sensor.TYPE_ACCELEROMETER]. The listener is registered/unregistered in a
 * [DisposableEffect] tied to composition lifetime.
 *
 * Returns [Offset.Zero] (and registers nothing) when:
 * - no usable sensor is present (e.g. an emulator), or
 * - the system "remove animations" accessibility setting is on (also covers snapshot/preview builds).
 *
 * No manifest permission is required for these motion sensors.
 */
@Composable
fun rememberDeviceTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }

    // Honour Reduce Motion / snapshot builds: stay perfectly still.
    val animationsEnabled = remember { context.areAnimationsEnabled() }

    DisposableEffect(animationsEnabled) {
        if (!animationsEnabled) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService<SensorManager>()
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || sensor == null) {
            // No usable sensor (typically an emulator): leave the logo perfectly still.
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }

        // Smoothed (low-passed) state, updated on each sample. We keep it local so the easing is
        // continuous across emissions and never re-allocates per frame.
        var smoothedRoll = 0f
        var smoothedPitch = 0f

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                val targetRoll: Float
                val targetPitch: Float
                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        // orientation = [azimuth, pitch, roll] in radians.
                        targetPitch = (orientation[1] / MAX_TILT_RADIANS).coerceIn(-1f, 1f)
                        targetRoll = (orientation[2] / MAX_TILT_RADIANS).coerceIn(-1f, 1f)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        // Map the gravity vector to roll/pitch. x left/right, y up/down, gravity ~9.81.
                        targetRoll = (-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                        targetPitch = (event.values[1] / SensorManager.GRAVITY_EARTH - 1f).coerceIn(-1f, 1f)
                    }
                    else -> return
                }

                smoothedRoll += (targetRoll - smoothedRoll) * TILT_EASING
                smoothedPitch += (targetPitch - smoothedPitch) * TILT_EASING
                tilt.value = Offset(smoothedRoll, smoothedPitch)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // No-op: tilt is decorative, accuracy changes do not matter.
            }
        }

        Timber.d("rememberDeviceTilt: registering listener on sensor type=%d", sensor.type)
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            Timber.d("rememberDeviceTilt: unregistering listener")
            sensorManager.unregisterListener(listener)
            tilt.value = Offset.Zero
        }
    }

    return tilt
}
