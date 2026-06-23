/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.atomic.atoms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.element.android.libraries.androidutils.system.areAnimationsEnabled
import io.element.android.libraries.designsystem.modifiers.blurCompat
import io.element.android.libraries.designsystem.modifiers.canUseBlur
import io.element.android.libraries.designsystem.modifiers.rememberDeviceTilt
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlin.math.abs
import kotlin.math.max

// Gua brand green, used for the soft aura that spills past the logo tile. Mirrors the bright green
// of the welcome aurora (see GuaWelcomeBackground) so the logo reads as part of the same world.
private val GuaAuraGreen = Color(0xFF1F9D5B)

// How far (as a fraction of the tile size) the soft aura glow spills past the tile edges.
private const val AURA_SPILL_FRACTION = 0.28f

// Max parallax rotation, in degrees, at full tilt. Kept small so the effect is felt, not flashy.
private const val PARALLAX_DEGREES = 5f

/**
 * The branded Gua welcome logo: the [ElementLogoAtom] glass tile elevated with a soft Gua-green
 * aura, a tilt-driven glass highlight, and a gentle 3D parallax — plus a one-shot slide-in
 * entrance. This is the Android analogue of the iOS welcome screen's logo treatment.
 *
 * All motion (device tilt, the highlight, the entrance) is gated on
 * [areAnimationsEnabled][io.element.android.libraries.androidutils.system.areAnimationsEnabled]. Under
 * Reduce Motion / snapshot / preview builds the logo renders as a clean, fully-visible static tile
 * (with the static aura), so screenshot references stay stable. There is no breathing/pulsing loop:
 * the only animation is device motion plus the single entrance.
 *
 * @param size the [ElementLogoAtomSize] of the underlying glass tile.
 */
@Composable
fun GuaWelcomeLogo(
    modifier: Modifier = Modifier,
    size: ElementLogoAtomSize = ElementLogoAtomSize.Large,
) {
    val context = LocalContext.current
    val animationsEnabled = remember { context.areAnimationsEnabled() }

    // rememberDeviceTilt() already returns Offset.Zero when animations are disabled or no sensor is
    // present, so it is safe (and rules-of-composition-correct) to call it unconditionally.
    val tilt by rememberDeviceTilt()

    // One-shot entrance flag: flipped true once, on first composition, when motion is allowed.
    var entered by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) {
        entered = true
    }

    AnimatedVisibility(
        visible = entered,
        // When motion is off these specs are never exercised (entered starts true), so the tile is
        // simply present and fully opaque from the first frame.
        enter = if (animationsEnabled) {
            slideInHorizontally(
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 900f),
                initialOffsetX = { full -> full / 2 },
            ) + fadeIn(animationSpec = tween(durationMillis = 350)) +
                scaleIn(animationSpec = tween(durationMillis = 350), initialScale = 0.86f)
        } else {
            fadeIn(animationSpec = tween(durationMillis = 0))
        },
    ) {
        GuaWelcomeLogoVisual(
            size = size,
            tilt = if (animationsEnabled) tilt else Offset.Zero,
            modifier = modifier,
        )
    }
}

@Composable
private fun GuaWelcomeLogoVisual(
    size: ElementLogoAtomSize,
    tilt: Offset,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val roll = tilt.x
    val pitch = tilt.y
    // Magnitude in 0..1, used to fade the highlight up as the device is tilted.
    val tiltMagnitude = max(abs(roll), abs(pitch)).coerceIn(0f, 1f)

    // The aura layer is explicitly larger than the tile so the glow can spill past its edges; the
    // outer Box wraps to this larger aura, and the tile is centred within it.
    val auraSize = size.outerSize * (1f + AURA_SPILL_FRACTION * 2f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Soft Gua-green aura, drawn as its own (larger) layer behind the tile so the blur only
        // softens the glow — never the crisp logo tile/highlight in front of it. drawWithCache
        // rebuilds the radial brush only when the layout size changes, not every frame.
        Box(
            modifier = Modifier
                .size(auraSize)
                .drawWithCache {
                    val w = this.size.width
                    val h = this.size.height
                    val center = Offset(w / 2f, h / 2f)
                    val radius = max(w, h) / 2f
                    val auraBrush = Brush.radialGradient(
                        colors = listOf(
                            GuaAuraGreen.copy(alpha = 0.45f),
                            GuaAuraGreen.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    )
                    onDrawBehind {
                        drawCircle(brush = auraBrush, radius = radius, center = center)
                    }
                }
                // Blur the aura on capable devices (API 31+) for a softer spill. On older devices
                // the raw radial gradient already reads as a soft glow.
                .then(if (canUseBlur()) Modifier.blurCompat(24.dp) else Modifier),
        )
        Box(
            modifier = Modifier
                // 3D parallax: tilt the tile slightly toward/away from the viewer.
                .graphicsLayer {
                    rotationX = pitch * PARALLAX_DEGREES
                    rotationY = roll * PARALLAX_DEGREES
                    cameraDistance = 8 * density
                }
                // Tilt-driven glass highlight: a soft white hotspot offset by the tilt vector, its
                // opacity scaled by how far the device is tilted, screened over the glass tile.
                .drawWithCache {
                    val w = this.size.width
                    val h = this.size.height
                    onDrawWithContent {
                        drawContent()
                        if (tiltMagnitude > 0.001f) {
                            val hotspot = Offset(
                                x = w * (0.5f + roll * 0.35f),
                                y = h * (0.5f - pitch * 0.35f),
                            )
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.55f * tiltMagnitude),
                                        Color.Transparent,
                                    ),
                                    center = hotspot,
                                    radius = max(w, h) * 0.55f,
                                ),
                                size = Size(w, h),
                                blendMode = BlendMode.Screen,
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            ElementLogoAtom(
                size = size,
                // Force the lighter glass treatment so the logo reads as a premium object on the
                // dark aurora regardless of the active light/dark theme.
                darkTheme = false,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun GuaWelcomeLogoPreview() = ElementPreview {
    // Motion is off in previews/snapshots (areAnimationsEnabled() is false there), so this renders
    // the clean static tile + aura, fully visible.
    Box(
        modifier = Modifier,
        contentAlignment = Alignment.Center,
    ) {
        GuaWelcomeLogo(size = ElementLogoAtomSize.Large)
    }
}
