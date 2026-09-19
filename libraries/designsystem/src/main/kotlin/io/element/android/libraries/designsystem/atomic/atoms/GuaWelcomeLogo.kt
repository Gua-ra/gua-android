/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.atomic.atoms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.libraries.androidutils.system.areAnimationsEnabled
import io.element.android.libraries.designsystem.R
import io.element.android.libraries.designsystem.modifiers.rememberDeviceTilt
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

// Max resting parallax rotation, in degrees, at full device tilt. Small so it's felt, not flashy.
private const val PARALLAX_DEGREES = 5f

// The Y-axis angle the logo starts at during the entrance, so it visibly flips in (the "3D effect").
private const val ENTRANCE_FLIP_DEGREES = 60f

/**
 * The branded Gua welcome logo: the full app-icon mark (`element_logo`) rendered CLEANLY — no glass
 * tile, no glow, no spotlight — with a soft drop shadow for depth.
 *
 * It arrives with an **elegant one-shot 3D entrance**: the logo flies in from the side while flipping
 * on its Y axis (a real perspective rotation that settles), fading and scaling up. After that it moves
 * **only** with the device — a home-screen-icon parallax (3D tilt), the iOS welcome-logo analogue.
 *
 * All motion (entrance + tilt) is gated on
 * [areAnimationsEnabled][io.element.android.libraries.androidutils.system.areAnimationsEnabled]; under
 * Reduce Motion / snapshots it's a clean static mark (entrance starts already settled). Note: the
 * resting tilt needs a gyroscope, so on the emulator only the entrance shows.
 */
@Composable
fun GuaWelcomeLogo(
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
) {
    val context = LocalContext.current
    val animationsEnabled = remember { context.areAnimationsEnabled() }

    // rememberDeviceTilt() returns Offset.Zero with no sensor (emulator) or when motion is disabled.
    val tilt by rememberDeviceTilt()

    // Entrance progress 0 -> 1, driven once on first composition (already 1 when motion is off).
    val entrance = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            entrance.animateTo(1f, animationSpec = spring(dampingRatio = 0.68f, stiffness = 220f))
        }
    }

    val density = LocalDensity.current.density
    val roll = if (animationsEnabled) tilt.x else 0f
    val pitch = if (animationsEnabled) tilt.y else 0f

    Image(
        painter = painterResource(R.drawable.element_logo),
        contentDescription = null,
        modifier = modifier
            .size(size)
            // Soft drop shadow matching the icon's rounded corners — depth, not a glow.
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(percent = 22), clip = false)
            .graphicsLayer {
                val p = entrance.value
                // Fly in from the leading (left) side while flipping in 3D, fading + scaling up.
                translationX = -(1f - p) * this.size.width * 1.15f
                rotationY = (1f - p) * ENTRANCE_FLIP_DEGREES + roll * PARALLAX_DEGREES
                rotationX = pitch * PARALLAX_DEGREES
                val scale = 0.82f + 0.18f * p
                scaleX = scale
                scaleY = scale
                alpha = p
                // A nearer camera makes the Y-flip read as real perspective rather than a flat skew.
                cameraDistance = 12f * density
            },
    )
}

@PreviewsDayNight
@Composable
internal fun GuaWelcomeLogoPreview() = ElementPreview {
    GuaWelcomeLogo()
}
