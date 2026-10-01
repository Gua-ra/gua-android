/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.background

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.androidutils.system.areAnimationsEnabled
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlin.math.cos
import kotlin.math.sin

private val GuaDeepGreen = Color(0xFF0E3A23)
private val GuaGreen = Color(0xFF11512F)
private val GuaBrightGreen = Color(0xFF1F9D5B)
private val GuaTeal = Color(0xFF0D9AA6)

@Suppress("ModifierMissing")
@Composable
fun GuaWelcomeBackground(
    animated: Boolean = true,
) {
    val context = LocalContext.current
    val isLive = remember(animated) { animated && context.areAnimationsEnabled() }

    val transition = rememberInfiniteTransition(label = "gua-aurora")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gua-aurora-phase",
    )
    val phase = if (isLive) animatedPhase else 0f

    val canvasColor = if (ElementTheme.isLightTheme) GuaDeepGreen else Color(0xFF071D12)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasColor)
            .drawBehind {
                val w = size.width
                val h = size.height
                val drift = w * 0.10f

                val greenCenter = Offset(
                    x = w * 0.32f + cos(phase) * drift,
                    y = h * 0.30f + sin(phase) * drift * 0.7f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GuaBrightGreen.copy(alpha = 0.55f),
                            GuaGreen.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                        center = greenCenter,
                        radius = w * 0.85f,
                    ),
                    radius = w * 0.85f,
                    center = greenCenter,
                )

                val tealCenter = Offset(
                    x = w * 0.72f + cos(phase + 2.1f) * drift,
                    y = h * 0.74f + sin(phase + 1.3f) * drift * 0.8f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GuaTeal.copy(alpha = 0.42f),
                            GuaTeal.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = tealCenter,
                        radius = w * 0.75f,
                    ),
                    radius = w * 0.75f,
                    center = tealCenter,
                )

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, canvasColor.copy(alpha = 0.55f)),
                        startY = h * 0.55f,
                        endY = h,
                    ),
                )
            }
    )
}

@PreviewsDayNight
@Composable
internal fun GuaWelcomeBackgroundPreview() = ElementPreview {
    Box(modifier = Modifier.fillMaxSize()) {
        GuaWelcomeBackground(animated = false)
    }
}
