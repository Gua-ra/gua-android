/*
 * Copyright 2026 Gua
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

private const val MAX_TILT_RADIANS = 0.6f

private const val TILT_EASING = 0.15f

@Composable
fun rememberDeviceTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }

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
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }

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
