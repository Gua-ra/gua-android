/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

private val GRAIN_STYLE = wallpaperGrainStyle()

/**
 * An artwork's still image, decoded by Coil off the main thread at the frame's size and cropped around its anchor.
 * Its fallback preset shows until the first image has loaded, and if it never does; a new size or appearance keeps the previous image until
 * its own has loaded.
 */
@Composable
internal fun ArtBackground(
    artwork: ChatArtwork,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().wallpaperGrain(rememberWallpaperGrain())) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceIn(1, MAX_DECODED_EDGE_PX)
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceIn(1, MAX_DECODED_EDGE_PX)
        if (LocalInspectionMode.current) {
            InspectedArtStill(artwork = artwork, widthPx = widthPx, heightPx = heightPx)
        } else {
            LoadedArtStill(artwork = artwork, widthPx = widthPx, heightPx = heightPx)
        }
        ChatBackgroundScrim(photoDim = artwork.darkDim)
    }
}

/** A built-in photo is dimmed in dark mode like a user's own photo at its default; other art is drawn for each appearance. */
internal val ChatArtwork.darkDim: Int?
    get() = if (isPhoto) ChatBackground.Photo.DEFAULT_DIM else null

@Composable
private fun LoadedArtStill(artwork: ChatArtwork, widthPx: Int, heightPx: Int) {
    val isLight = ElementTheme.isLightTheme
    val file = artwork.file(isLight)
    var isLoaded by remember(file) { mutableStateOf(false) }
    val context = LocalContext.current
    val request = remember(file, widthPx, heightPx) { wallpaperStillRequest(context, file, widthPx, heightPx) }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .then(if (isLoaded) Modifier else Modifier.drawChatBackgroundFill(artwork.fallbackPreset.fill(isLight))),
        contentScale = ContentScale.Crop,
        alignment = chatBackgroundAlignment(artwork.anchor),
        onSuccess = { isLoaded = true },
    )
}

/** In a preview Coil answers every request with the preview handler's sample image, so the still is decoded in place. */
@Composable
private fun InspectedArtStill(artwork: ChatArtwork, widthPx: Int, heightPx: Int) {
    val isLight = ElementTheme.isLightTheme
    val file = artwork.file(isLight)
    val context = LocalContext.current
    val still = remember(context, file, widthPx, heightPx) { decodeWallpaperStill(context, file, widthPx, heightPx) }
    if (still == null) {
        Box(Modifier.fillMaxSize().drawChatBackgroundFill(artwork.fallbackPreset.fill(isLight)))
    } else {
        Image(
            bitmap = still,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = chatBackgroundAlignment(artwork.anchor),
        )
    }
}

@OptIn(ExperimentalCoilApi::class)
internal fun wallpaperStillRequest(context: Context, file: String, widthPx: Int, heightPx: Int): ImageRequest = ImageRequest.Builder(context)
    .data("file:///android_asset/${wallpaperAssetPath(file)}")
    .size(widthPx, heightPx)
    .useExistingImageAsPlaceholder(true)
    .build()

/** Decodes the still on the calling thread, downsampled while it still covers the frame; null if it cannot be read. */
internal fun decodeWallpaperStill(context: Context, file: String, widthPx: Int, heightPx: Int): ImageBitmap? = runCatchingExceptions {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(wallpaperAssetPath(file)).use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply { inSampleSize = wallpaperSampleSize(bounds.outWidth, bounds.outHeight, widthPx, heightPx) }
    context.assets.open(wallpaperAssetPath(file)).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}
    .onFailure { Timber.w(it, "Could not load the wallpaper %s", file) }
    .getOrNull()

/** The largest power of two that the image can be divided by and still cover a frame of [frameWidth] x [frameHeight]. */
internal fun wallpaperSampleSize(imageWidth: Int, imageHeight: Int, frameWidth: Int, frameHeight: Int): Int {
    var sampleSize = 1
    while (imageWidth / (sampleSize * 2) >= frameWidth && imageHeight / (sampleSize * 2) >= frameHeight) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun wallpaperAssetPath(file: String) = "${ChatArtwork.ASSETS_FOLDER}/$file"

/** The shared grain tile, or null until it has loaded off the main thread. */
@Composable
internal fun rememberWallpaperGrain(): ImageBitmap? {
    val context = LocalContext.current
    if (LocalInspectionMode.current) {
        // A screenshot is a single frame, so the tile is decoded in place: whether grain shows never depends on which preview ran first.
        return remember(context) { WallpaperNoise.cached ?: WallpaperNoise.decode(context) }
    }
    val tile by produceState(initialValue = WallpaperNoise.cached, context) {
        if (value == null) value = withContext(Dispatchers.IO) { WallpaperNoise.cached ?: WallpaperNoise.decode(context) }
    }
    return tile
}

/** Draws [grain] repeated at device pixels over the content; nothing while it is null. */
internal fun Modifier.wallpaperGrain(grain: ImageBitmap?): Modifier {
    grain ?: return this
    return drawWithCache {
        val brush = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, alpha = GRAIN_STYLE.alpha, blendMode = GRAIN_STYLE.blendMode)
        }
    }
}

private object WallpaperNoise {
    @Volatile
    var cached: ImageBitmap? = null
        private set

    fun decode(context: Context): ImageBitmap? = runCatchingExceptions {
        context.assets.open(wallpaperAssetPath(ChatArtwork.NOISE_FILE)).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }
        .onFailure { Timber.w(it, "Could not load the wallpaper grain") }
        .getOrNull()
        ?.also { cached = it }
}
