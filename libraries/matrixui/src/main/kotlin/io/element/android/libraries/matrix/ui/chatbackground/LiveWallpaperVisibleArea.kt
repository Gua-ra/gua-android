/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * Where a screen's chat shows over the live wallpaper, in root coordinates; the send pulse starts in it. An edge the screen does not
 * report is the canvas's own, less the system bottom inset at the bottom.
 */
@Stable
class LiveWallpaperVisibleArea {
    var top: Float? by mutableStateOf(null)
        internal set
    var bottom: Float? by mutableStateOf(null)
        internal set
}

@Composable
fun rememberLiveWallpaperVisibleArea(): LiveWallpaperVisibleArea = remember { LiveWallpaperVisibleArea() }

fun Modifier.liveWallpaperChatArea(area: LiveWallpaperVisibleArea): Modifier = onGloballyPositioned { coordinates ->
    val top = coordinates.positionInRoot().y
    area.top = top
    area.bottom = top + coordinates.size.height
}

/** Ends the chat area at this element's top, as a preview's controls do. */
fun Modifier.liveWallpaperChatAreaEnd(area: LiveWallpaperVisibleArea): Modifier = onGloballyPositioned { coordinates ->
    area.bottom = coordinates.positionInRoot().y
}

/** A chat area in canvas pixels from the canvas's top. [bottomInset] applies when there is no [bottom]. */
internal data class LiveWallpaperCanvasArea(val top: Float?, val bottom: Float?, val bottomInset: Float)

/** [area] on a canvas whose top is at [canvasRootTop] in root coordinates and whose bottom is [spaceBelowCanvas] above the root's. */
internal fun liveWallpaperCanvasArea(
    canvasRootTop: Float,
    area: LiveWallpaperVisibleArea?,
    systemBottomInset: Float,
    spaceBelowCanvas: Float,
): LiveWallpaperCanvasArea = LiveWallpaperCanvasArea(
    top = area?.top?.minus(canvasRootTop),
    bottom = area?.bottom?.minus(canvasRootTop),
    // Only the part of the inset the canvas reaches into, so a canvas padded for the navigation bar has none.
    bottomInset = (systemBottomInset - spaceBelowCanvas).coerceAtLeast(0f),
)

/** Lets tests read the visible area a chat background was drawn with; accessibility services ignore it. */
val LiveWallpaperVisibleAreaKey = SemanticsPropertyKey<LiveWallpaperVisibleArea>("LiveWallpaperVisibleArea")

internal var SemanticsPropertyReceiver.liveWallpaperVisibleArea by LiveWallpaperVisibleAreaKey
