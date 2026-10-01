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

private const val PARALLAX_DEGREES = 5f

private const val ENTRANCE_FLIP_DEGREES = 60f

@Composable
fun GuaWelcomeLogo(
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
) {
    val context = LocalContext.current
    val animationsEnabled = remember { context.areAnimationsEnabled() }

    val tilt by rememberDeviceTilt()

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
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(percent = 22), clip = false)
            .graphicsLayer {
                val p = entrance.value
                translationX = -(1f - p) * this.size.width * 1.15f
                rotationY = (1f - p) * ENTRANCE_FLIP_DEGREES + roll * PARALLAX_DEGREES
                rotationX = pitch * PARALLAX_DEGREES
                val scale = 0.82f + 0.18f * p
                scaleX = scale
                scaleY = scale
                alpha = p
                cameraDistance = 12f * density
            },
    )
}

@PreviewsDayNight
@Composable
internal fun GuaWelcomeLogoPreview() = ElementPreview {
    GuaWelcomeLogo()
}
