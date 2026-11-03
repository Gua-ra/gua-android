/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.chatbackground

import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import kotlin.math.min

/** True while the timeline is drawn over a chat background. Elements drawn directly on the timeline then get a capsule. */
val LocalChatBackgroundActive = staticCompositionLocalOf { false }

private const val CAPSULE_OPACITY = 0.88f
private val CAPSULE_MAX_CORNER_RADIUS = 12.dp
private val CAPSULE_PADDING = PaddingValues(horizontal = 8.dp, vertical = 3.dp)

/** Fully rounded while it holds a single line, so a notice that wraps keeps its corners clear of the text. */
val LegibilityCapsuleShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = min(size.height / 2, with(density) { CAPSULE_MAX_CORNER_RADIUS.toPx() })
        return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(radius)))
    }
}

/**
 * Draws a canvas-coloured capsule behind this element while [LocalChatBackgroundActive] is true, and nothing otherwise,
 * so the timeline looks exactly as before when the chat has no background.
 */
@Suppress("ModifierComposable")
@Composable
fun Modifier.legibilityCapsule(
    shape: Shape = LegibilityCapsuleShape,
    padding: PaddingValues = CAPSULE_PADDING,
): Modifier {
    if (!LocalChatBackgroundActive.current) return this
    val opacity = if (isIncreasedContrastEnabled()) 1f else CAPSULE_OPACITY
    return background(ElementTheme.colors.bgCanvasDefault.copy(alpha = opacity), shape).padding(padding)
}

@Composable
@ReadOnlyComposable
fun legibleTextColor(default: Color): Color = if (LocalChatBackgroundActive.current) ElementTheme.colors.textPrimary else default

/** Android 14's contrast setting above standard. */
@Composable
fun isIncreasedContrastEnabled(): Boolean {
    if (LocalInspectionMode.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        tryOrNull { context.getSystemService(UiModeManager::class.java)?.contrast } ?: 0f
    } > 0f
}

@PreviewsDayNight
@Composable
internal fun LegibilityCapsulePreview() = ElementPreview {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF1F9D5B), Color(0xFFF0C6A6)))),
    ) {
        CompositionLocalProvider(LocalChatBackgroundActive provides true) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Today",
                    modifier = Modifier.legibilityCapsule(),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Text(
                    text = "Alice changed the chat background and this notice is long enough to wrap onto a second line",
                    modifier = Modifier
                        .width(240.dp)
                        .legibilityCapsule(),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = legibleTextColor(ElementTheme.colors.textSecondary),
                )
            }
        }
    }
}
