/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import android.os.Build
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

private val BACKDROP_BLUR_RADIUS = 16.dp

@Composable
fun MessageMenuHost(
    controller: MessageMenuController,
    overlay: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMessageMenuController provides controller) {
        Box(modifier = modifier) {
            content()
            overlay()
        }
    }
}

fun Modifier.messageMenuBackdrop(
    controller: MessageMenuController,
    isMenuShown: Boolean,
): Modifier {
    if (!isMenuShown) return this
    val hidden = clearAndSetSemantics {}
        .focusProperties { onEnter = { cancelFocusChange() } }
        .focusGroup()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hidden.graphicsLayer {
            val radius = BACKDROP_BLUR_RADIUS.toPx() * controller.progress.value
            renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Clamp) else null
        }
    } else {
        hidden
    }
}
