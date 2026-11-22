/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
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
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.LocalUiTestMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

private val GRAIN_STYLE = wallpaperGrainStyle()

/**
 * An artwork's still image, decoded by Coil off the main thread at the frame's size and cropped around its anchor.
 * Its fallback preset shows until the first image has loaded, and if it never does; a new size or appearance keeps the previous image until
 * its own has loaded.
 * With [live] cues, on Android 13 and up, the artwork tilts with the device and a live one animates. Previews and UI tests always show
 * the still, unmoved.
 */
@Composable
internal fun ArtBackground(
    artwork: ChatArtwork,
    modifier: Modifier = Modifier,
    live: LiveWallpaperCues? = null,
    visibleArea: LiveWallpaperVisibleArea? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewWidthPx = with(density) { maxWidth.toPx() }.roundToInt()
        val viewHeightPx = with(density) { maxHeight.toPx() }.roundToInt()
        val moves = live != null && !LocalInspectionMode.current && !LocalUiTestMode.current
        if (moves && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            MovingArt(artwork = artwork, cues = live, visibleArea = visibleArea, viewWidthPx = viewWidthPx, viewHeightPx = viewHeightPx)
        } else {
            ArtStill(artwork = artwork, viewWidthPx = viewWidthPx, viewHeightPx = viewHeightPx, parallax = null)
        }
        ChatBackgroundScrim(photoDim = null)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun MovingArt(
    artwork: ChatArtwork,
    cues: LiveWallpaperCues,
    visibleArea: LiveWallpaperVisibleArea?,
    viewWidthPx: Int,
    viewHeightPx: Int,
) {
    val sensor = rememberWallpaperTiltSensor()
    // Overscanned even while a still condition stops the tilt, so the framing never changes on screen.
    val parallax = sensor?.let {
        WallpaperParallax(it, shift = wallpaperParallaxShift(viewWidthPx.toDouble(), viewHeightPx.toDouble()).roundToInt())
    }
    val motion = rememberWallpaperMotion(isCovered = cues.isCovered)
    if (sensor != null) {
        WallpaperTiltEffect(sensor = sensor, motion = motion)
    }
    val still = @Composable { ArtStill(artwork = artwork, viewWidthPx = viewWidthPx, viewHeightPx = viewHeightPx, parallax = parallax) }
    if (artwork.isLive) {
        LiveArt(artwork = artwork, cues = cues, motion = motion, visibleArea = visibleArea, parallax = parallax, still = still)
    } else {
        still()
    }
}

/** With a [parallax] the still and its grain overscan the view and move with the tilt. */
@Composable
private fun ArtStill(artwork: ChatArtwork, viewWidthPx: Int, viewHeightPx: Int, parallax: WallpaperParallax?) {
    val shift = parallax?.shift ?: 0
    val widthPx = (viewWidthPx + 2 * shift).coerceIn(1, MAX_DECODED_EDGE_PX)
    val heightPx = (viewHeightPx + 2 * shift).coerceIn(1, MAX_DECODED_EDGE_PX)
    Box(
        modifier = Modifier
            .then(if (parallax == null) Modifier.fillMaxSize() else Modifier.wallpaperParallax(parallax))
            // The overscanned box starts at (-shift, -shift), so at rest its tile starts where the live view's does.
            .wallpaperGrain(rememberWallpaperGrain(), tileOrigin = Offset(shift.toFloat(), shift.toFloat())),
    ) {
        if (LocalInspectionMode.current) {
            InspectedArtStill(artwork = artwork, widthPx = widthPx, heightPx = heightPx)
        } else {
            LoadedArtStill(artwork = artwork, widthPx = widthPx, heightPx = heightPx)
        }
    }
}

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

/** Draws [grain] repeated at device pixels over the content, a tile starting at [tileOrigin]; nothing while it is null. */
internal fun Modifier.wallpaperGrain(grain: ImageBitmap?, tileOrigin: Offset = Offset.Zero): Modifier {
    grain ?: return this
    return drawWithCache {
        val brush = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
        onDrawWithContent {
            drawContent()
            translate(left = tileOrigin.x, top = tileOrigin.y) {
                drawRect(brush = brush, topLeft = -tileOrigin, size = size, alpha = GRAIN_STYLE.alpha, blendMode = GRAIN_STYLE.blendMode)
            }
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
