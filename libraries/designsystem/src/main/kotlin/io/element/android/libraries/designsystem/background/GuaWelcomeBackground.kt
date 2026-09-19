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

// Gua brand greens for the welcome aurora. Mirrors the iOS welcome screen "aurora" in feel:
// a calm, premium, slowly-drifting glow in Gua greens, with a teal accent.
private val GuaDeepGreen = Color(0xFF0E3A23)
private val GuaGreen = Color(0xFF11512F)
private val GuaBrightGreen = Color(0xFF1F9D5B)
private val GuaTeal = Color(0xFF0D9AA6)

/**
 * A soft, premium "aurora" background in Gua greens for the welcome/first-run screen.
 *
 * Two large radial glow blobs (a bright Gua green and a teal accent) drift slowly over a deep-green
 * canvas, evoking the same calm, alive feel as the iOS welcome screen. The motion is deliberately
 * low and tasteful: a single very slow cycle, eased linearly, with no pulsing.
 *
 * Respects the system "remove animations" accessibility setting (and snapshot/preview builds) by
 * falling back to a still composition of the same glows, so screenshot references stay stable.
 *
 * @param animated when false the aurora is drawn static (also forced off when animations are
 * disabled in system settings).
 */
@Suppress("ModifierMissing")
@Composable
fun GuaWelcomeBackground(
    animated: Boolean = true,
) {
    val context = LocalContext.current
    val isLive = remember(animated) { animated && context.areAnimationsEnabled() }

    // A single slowly-advancing phase drives both glows. When not live, it stays at 0 so the
    // aurora is drawn as a stable still composition (snapshot-safe, Reduce-Motion-safe).
    val transition = rememberInfiniteTransition(label = "gua-aurora")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            // ~24s for a full, barely-perceptible orbit — calm, not busy.
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gua-aurora-phase",
    )
    val phase = if (isLive) animatedPhase else 0f

    // The deep canvas. In light theme we keep it a touch lighter so text reads cleanly,
    // while the greens still come through via the glows.
    val canvasColor = if (ElementTheme.isLightTheme) GuaDeepGreen else Color(0xFF071D12)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasColor)
            .drawBehind {
                val w = size.width
                val h = size.height
                val drift = w * 0.10f

                // Primary Gua-green glow, upper area, drifting in a slow ellipse.
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

                // Teal accent glow, lower area, drifting on a different slow phase.
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

                // A subtle deepening vignette at the bottom so the footer buttons sit calmly.
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
        // Static in previews/snapshots for stable references.
        GuaWelcomeBackground(animated = false)
    }
}
