/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStickerContent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val DEFAULT_PANEL_HEIGHT = 300.dp

/** The tallest the keyboard has been on this screen, which the expression panel takes in its place. */
@Composable
internal fun rememberLastKeyboardHeight(): Dp {
    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.exclude(WindowInsets.navigationBars).getBottom(density)
    var lastHeight by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(imeHeight) {
        if (imeHeight > lastHeight) lastHeight = imeHeight
    }
    return if (lastHeight > 0) with(density) { lastHeight.toDp() } else DEFAULT_PANEL_HEIGHT
}

/** A sticker's image object, when it is plain media that can be saved and sent again. */
internal fun TimelineItemStickerContent.toStickerImage(): StickerImage? {
    val url = mediaSource.safeUrl.takeIf { mediaSource.json == null && it.startsWith("mxc://") } ?: return null
    return StickerImage(
        url = url,
        body = caption,
        info = buildJsonObject {
            height?.let { put("h", it) }
            put("mimetype", mimeType)
            fileSize?.let { put("size", it) }
            width?.let { put("w", it) }
        },
    )
}
