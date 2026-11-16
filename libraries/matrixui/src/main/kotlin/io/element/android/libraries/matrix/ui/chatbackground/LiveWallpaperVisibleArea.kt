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
 * The part of a screen where its chat shows over the live wallpaper, in root coordinates. The send pulse starts in it, where the newest
 * outgoing bubble sits. A screen reports it with [liveWallpaperChatArea] or [liveWallpaperChatAreaEnd] and hands it to its
 * [ChatBackgroundCanvas]. An edge the screen never reports is the canvas's own, less the system bottom inset at the bottom.
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

/** This element is where the chat shows, such as a chat screen's content between its top bar and its composer. */
fun Modifier.liveWallpaperChatArea(area: LiveWallpaperVisibleArea): Modifier = onGloballyPositioned { coordinates ->
    val top = coordinates.positionInRoot().y
    area.top = top
    area.bottom = top + coordinates.size.height
}

/** This element covers the bottom of the chat, such as a preview's controls, so the chat shows down to its top edge. */
fun Modifier.liveWallpaperChatAreaEnd(area: LiveWallpaperVisibleArea): Modifier = onGloballyPositioned { coordinates ->
    area.bottom = coordinates.positionInRoot().y
}

/**
 * A chat area as a canvas takes it: its [top] and [bottom] in the canvas's pixels measured from its top, null where the screen reports
 * none, and the [bottomInset] the chat ends above when the screen reports no bottom.
 */
internal data class LiveWallpaperCanvasArea(val top: Float?, val bottom: Float?, val bottomInset: Float)

/**
 * The [area] a screen reported in root coordinates, taken on a canvas whose top is at [canvasRootTop] in root coordinates and whose
 * bottom is [spaceBelowCanvas] above the root's. The bottom inset is the part of [systemBottomInset] the canvas reaches into.
 */
internal fun liveWallpaperCanvasArea(
    canvasRootTop: Float,
    area: LiveWallpaperVisibleArea?,
    systemBottomInset: Float,
    spaceBelowCanvas: Float,
): LiveWallpaperCanvasArea = LiveWallpaperCanvasArea(
    top = area?.top?.minus(canvasRootTop),
    bottom = area?.bottom?.minus(canvasRootTop),
    // A canvas that already stops above the system bar, such as one padded for it, has none of the inset to leave.
    bottomInset = (systemBottomInset - spaceBelowCanvas).coerceAtLeast(0f),
)

/** The visible area a chat background was drawn with. Not read by accessibility services; tests use it to check what a screen reports. */
val LiveWallpaperVisibleAreaKey = SemanticsPropertyKey<LiveWallpaperVisibleArea>("LiveWallpaperVisibleArea")

internal var SemanticsPropertyReceiver.liveWallpaperVisibleArea by LiveWallpaperVisibleAreaKey
