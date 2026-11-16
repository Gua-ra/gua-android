/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import coil3.ImageLoader
import coil3.compose.AsyncImage
import io.element.android.features.expressions.api.gif.GifFile
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.impl.R
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrixmedia.api.LocalPlayGifsAutomatically
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import io.element.android.libraries.matrixmedia.api.animatedImageModel
import kotlin.math.absoluteValue

@Composable
internal fun StickerImageView(
    image: StickerImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Image(painterResource(previewDrawable(image.url)), contentDescription, modifier, contentScale = ContentScale.Fit)
        return
    }
    val context = LocalContext.current
    val play = LocalPlayGifsAutomatically.current
    val model = remember(image.url, image.mimetype, play) {
        val data = MediaRequestData(
            source = MediaSource(image.url),
            kind = MediaRequestData.Kind.File(fileName = image.body ?: "sticker", mimeType = image.mimetype ?: MimeTypes.Png),
        )
        context.animatedImageModel(data, play)
    }
    AsyncImage(model = model, contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Fit)
}

/**
 * Provider media only ever loads through the GIF client, never the app's own image loader.
 * Without [play] it shows the first frame only; a GIF the user opened plays whatever the setting.
 */
@Composable
internal fun GifImageView(
    file: GifFile,
    imageLoader: ImageLoader,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    play: Boolean = LocalPlayGifsAutomatically.current,
) {
    if (LocalInspectionMode.current) {
        Image(painterResource(previewDrawable(file.url)), contentDescription, modifier, contentScale = ContentScale.Fit)
        return
    }
    val context = LocalContext.current
    val model = remember(file.url, play) { context.animatedImageModel(file.url, play) }
    AsyncImage(model = model, contentDescription = contentDescription, imageLoader = imageLoader, modifier = modifier, contentScale = ContentScale.Crop)
}

@DrawableRes
private fun previewDrawable(key: String): Int = when (key.hashCode().absoluteValue % 3) {
    0 -> R.drawable.gua_preview_sticker_face
    1 -> R.drawable.gua_preview_sticker_heart
    else -> R.drawable.gua_preview_sticker_star
}
