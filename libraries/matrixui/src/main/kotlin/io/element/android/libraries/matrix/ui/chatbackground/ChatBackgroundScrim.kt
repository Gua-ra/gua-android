/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.chatbackground.isIncreasedContrastEnabled
import kotlin.math.max

private const val INCREASED_CONTRAST_MIN_DIM = 0.5f
private const val INCREASED_CONTRAST_LIGHT_SCRIM = 0.06f

/** The dark mode dim of a photo, [photoDim] in percent or null for a preset, and the light mode scrim under increased contrast. */
@Composable
fun ChatBackgroundScrim(
    photoDim: Int?,
    modifier: Modifier = Modifier,
) {
    val isIncreasedContrast = isIncreasedContrastEnabled()
    val opacity = when {
        ElementTheme.isLightTheme -> if (isIncreasedContrast) INCREASED_CONTRAST_LIGHT_SCRIM else 0f
        photoDim == null -> 0f
        isIncreasedContrast -> max(photoDim / 100f, INCREASED_CONTRAST_MIN_DIM)
        else -> photoDim / 100f
    }
    if (opacity > 0f) {
        Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = opacity)))
    }
}
