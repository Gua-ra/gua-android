/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.components.blurhash.BlurHashImage
import kotlin.math.max

/** The blurhash of a photo still loading, laid out where the photo of [width] x [height] will be. */
@Composable
fun ChatBackgroundBlurHash(
    blurhash: String?,
    width: Int,
    height: Int,
    alignX: Int,
    alignY: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val scale = max(maxWidth.value / width, maxHeight.value / height)
        Box(
            modifier = Modifier
                .wrapContentSize(chatBackgroundPhotoAlignment(alignX, alignY), unbounded = true)
                .requiredSize(width = (width * scale).dp, height = (height * scale).dp),
        ) {
            BlurHashImage(blurHash = blurhash, contentScale = ContentScale.FillBounds)
        }
    }
}
