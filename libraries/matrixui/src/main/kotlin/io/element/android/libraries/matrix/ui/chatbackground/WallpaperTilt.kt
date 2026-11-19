/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.view.Surface
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

private const val TILT_MAX_ANGLE_DEGREES = 20.0
private const val TILT_RECENTRE_SECONDS = 2.0
private const val TILT_SMOOTHING_SECONDS = 0.06
private const val TILT_MAX_STEP_SECONDS = 0.1
private const val PARALLAX_SHIFT_SHARE = 0.015

private val FULL_TILT = sin(TILT_MAX_ANGLE_DEGREES * PI / 180)

/** A tilt, each component in -1..1: (1, 0) is the viewer to the right of the screen, (0, 1) the viewer above it. */
internal data class WallpaperTiltVector(val x: Double, val y: Double) {
    companion object {
        val Zero = WallpaperTiltVector(0.0, 0.0)
    }
}

/**
 * The device's tilt from its gyroscope, in its own frame. The viewer's direction starts on the screen's normal and stays still in the world
 * while the device turns, then slowly comes back to the normal, so the picture recentres on however the phone is held.
 */
internal class WallpaperTiltTracker {
    var tilt = WallpaperTiltVector.Zero
        private set

    private var viewerX = 0.0
    private var viewerY = 0.0
    private var viewerZ = 1.0
    private var lastSeconds: Double? = null

    /**
     * One gyroscope sample at [timeSeconds]: the rotation rate in rad/s around the device's x (right), y (top) and z (out of the screen)
     * axes, right-hand rule. The first sample after a start or a [stop] only sets the clock.
     */
    fun sample(timeSeconds: Double, rateX: Double, rateY: Double, rateZ: Double) {
        val last = lastSeconds
        lastSeconds = timeSeconds
        if (last == null) return
        val dt = min(max(timeSeconds - last, 0.0), TILT_MAX_STEP_SECONDS)
        // v -= (rate × v) dt: in the device's frame the viewer turns against the device.
        setViewer(
            x = viewerX - (rateY * viewerZ - rateZ * viewerY) * dt,
            y = viewerY - (rateZ * viewerX - rateX * viewerZ) * dt,
            z = viewerZ - (rateX * viewerY - rateY * viewerX) * dt,
        )
        val recentre = 1 - exp(-dt / TILT_RECENTRE_SECONDS)
        setViewer(x = viewerX * (1 - recentre), y = viewerY * (1 - recentre), z = viewerZ * (1 - recentre) + recentre)
        val targetX = tanh(viewerX / FULL_TILT)
        val targetY = tanh(viewerY / FULL_TILT)
        val smoothing = 1 - exp(-dt / TILT_SMOOTHING_SECONDS)
        tilt = WallpaperTiltVector(x = tilt.x + (targetX - tilt.x) * smoothing, y = tilt.y + (targetY - tilt.y) * smoothing)
    }

    /** Keeps the tilt, as a pause does; the next sample only sets the clock. */
    fun stop() {
        lastSeconds = null
    }

    /** Puts the smoothed tilt, in the device's frame, at [tilt]; the viewer and the clock are kept. */
    fun continueFrom(tilt: WallpaperTiltVector) {
        this.tilt = tilt
    }

    /** Back to rest: the viewer on the screen's normal and no tilt. */
    fun reset() {
        stop()
        setViewer(0.0, 0.0, 1.0)
        tilt = WallpaperTiltVector.Zero
    }

    private fun setViewer(x: Double, y: Double, z: Double) {
        val length = sqrt(x * x + y * y + z * z)
        viewerX = x / length
        viewerY = y / length
        viewerZ = z / length
    }
}

/** [tilt], in the device's frame, in the interface's frame on a screen turned by [surfaceRotation], a `Surface.ROTATION_*` value. */
internal fun wallpaperTiltInInterface(surfaceRotation: Int, tilt: WallpaperTiltVector): WallpaperTiltVector = when (surfaceRotation) {
    Surface.ROTATION_90 -> WallpaperTiltVector(x = -tilt.y, y = tilt.x)
    Surface.ROTATION_180 -> WallpaperTiltVector(x = -tilt.x, y = -tilt.y)
    Surface.ROTATION_270 -> WallpaperTiltVector(x = tilt.y, y = -tilt.x)
    else -> tilt
}

/** S: how far a still moves at full tilt over a view of [viewWidth] x [viewHeight], and how much larger than the view it is on every side. */
internal fun wallpaperParallaxShift(viewWidth: Double, viewHeight: Double): Double = PARALLAX_SHIFT_SHARE * max(viewWidth, viewHeight)

/** Where a still lands over its view, in the view's coordinates. */
internal data class WallpaperStillRect(val left: Double, val top: Double, val width: Double, val height: Double)

/**
 * The still over a view of [viewWidth] x [viewHeight]: aspect-filled into the view grown by [shift] on every side and cropped around the
 * anchor ([anchorX], [anchorY]), then moved by [tilt]: (-x × shift, y × shift), y down.
 */
internal fun wallpaperStillRect(
    viewWidth: Double,
    viewHeight: Double,
    anchorX: Double,
    anchorY: Double,
    shift: Double,
    tilt: WallpaperTiltVector = WallpaperTiltVector.Zero,
): WallpaperStillRect {
    val boxWidth = viewWidth + 2 * shift
    val boxHeight = viewHeight + 2 * shift
    val scale = max(boxWidth / LIVE_STILL_WIDTH.toDouble(), boxHeight / LIVE_STILL_HEIGHT.toDouble())
    val width = LIVE_STILL_WIDTH * scale
    val height = LIVE_STILL_HEIGHT * scale
    return WallpaperStillRect(
        left = -shift - (width - boxWidth) * anchorX - tilt.x * shift,
        top = -shift - (height - boxHeight) * anchorY + tilt.y * shift,
        width = width,
        height = height,
    )
}
