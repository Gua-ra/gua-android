/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.animation.ValueAnimator
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints

private const val SETTLE_MILLIS = 300
private const val NANOS_PER_SECOND = 1_000_000_000.0

// Skips gyroscope noise that moves the picture by hundredths of a pixel, so a phone at rest does not redraw the chat at the sensor's rate.
private const val MIN_TILT_CHANGE = 0.001f

/** The gyroscope's tilt in the interface's frame. Read [value] only while drawing, so a new tilt redraws without recomposing. */
@Stable
internal class WallpaperTiltSensor(
    private val sensorManager: SensorManager,
    private val gyroscope: Sensor,
    private val view: View,
) : SensorEventListener {
    var value by mutableStateOf(Offset.Zero)
        private set

    private val tracker = WallpaperTiltTracker()

    fun start() {
        // Continues from wherever a cut-short settle left the picture, turned back into the device's frame, instead of jumping to rest.
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        tracker.continueFrom(wallpaperTiltInInterface((4 - rotation) % 4, WallpaperTiltVector(value.x.toDouble(), value.y.toDouble())))
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        tracker.stop()
    }

    suspend fun settle(animates: Boolean) {
        tracker.reset()
        val from = value
        if (animates && from != Offset.Zero) {
            animate(initialValue = 1f, targetValue = 0f, animationSpec = tween(SETTLE_MILLIS)) { share, _ -> value = from * share }
        }
        value = Offset.Zero
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (rateX, rateY, rateZ) = event.values
        tracker.sample(event.timestamp / NANOS_PER_SECOND, rateX.toDouble(), rateY.toDouble(), rateZ.toDouble())
        // Read on every sample: a half turn from one landscape to the other changes no configuration.
        val tilt = wallpaperTiltInInterface(view.display?.rotation ?: Surface.ROTATION_0, tracker.tilt)
        val next = Offset(tilt.x.toFloat(), tilt.y.toFloat())
        if ((next - value).getDistance() >= MIN_TILT_CHANGE) {
            value = next
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Null without a gyroscope, so the wallpaper never tilts and keeps its plain framing. */
@Composable
internal fun rememberWallpaperTiltSensor(): WallpaperTiltSensor? {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { gyroscope -> WallpaperTiltSensor(sensorManager, gyroscope, view) }
    }
}

/**
 * Listens to [sensor] only while [motion] animates. A pause holds the tilt; a still condition brings the picture back to rest, at once
 * when animations are off.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WallpaperTiltEffect(sensor: WallpaperTiltSensor, motion: WallpaperMotion) {
    val runs = motion == WallpaperMotion.Animating
    DisposableEffect(sensor, runs) {
        if (runs) sensor.start()
        onDispose {
            if (runs) sensor.stop()
        }
    }
    val isStill = motion == WallpaperMotion.Still
    LaunchedEffect(sensor, isStill) {
        if (isStill) sensor.settle(animates = ValueAnimator.areAnimatorsEnabled())
    }
}

/** [sensor] moves the wallpaper up to [shift] px each way, so it is laid out [shift] px larger than its view on every side. */
internal data class WallpaperParallax(val sensor: WallpaperTiltSensor, val shift: Int)

internal fun Modifier.wallpaperParallax(parallax: WallpaperParallax): Modifier = this
    .fillMaxSize()
    .clipToBounds()
    .layout { measurable, constraints ->
        val shift = parallax.shift
        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + 2 * shift, constraints.maxHeight + 2 * shift))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-shift, -shift) }
    }
    .graphicsLayer {
        val tilt = parallax.sensor.value
        translationX = -tilt.x * parallax.shift
        translationY = tilt.y * parallax.shift
    }
