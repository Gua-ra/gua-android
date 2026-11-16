/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

// Overlay keeps the mean colour, so the grain only dithers: at this opacity a mid-tone moves by about 1 to 2 levels.
private const val GRAIN_ALPHA = 0.04f

/**
 * An artwork's still image, decoded by Coil off the main thread at the frame's size and cropped around its anchor.
 * Its fallback preset shows until the image has loaded, and if it never does.
 */
@Composable
internal fun ArtBackground(
    artwork: ChatArtwork,
    modifier: Modifier = Modifier,
) {
    val isLight = ElementTheme.isLightTheme
    val file = artwork.file(isLight)
    var isLoaded by remember(file) { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().wallpaperGrain(rememberWallpaperGrain())) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceIn(1, MAX_DECODED_EDGE_PX)
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceIn(1, MAX_DECODED_EDGE_PX)
        if (!isLoaded) {
            Box(Modifier.fillMaxSize().drawChatBackgroundFill(artwork.fallbackPreset.fill(isLight)))
        }
        val context = LocalContext.current
        val request = remember(file, widthPx, heightPx) {
            ImageRequest.Builder(context)
                .data(wallpaperAssetUri(file))
                .size(widthPx, heightPx)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = chatBackgroundAlignment(artwork.anchor),
            onSuccess = { isLoaded = true },
        )
        ChatBackgroundScrim(photoDim = null)
    }
}

private fun wallpaperAssetUri(file: String) = "file:///android_asset/${ChatArtwork.ASSETS_FOLDER}/$file"

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
            drawRect(brush = brush, alpha = GRAIN_ALPHA, blendMode = BlendMode.Overlay)
        }
    }
}

private object WallpaperNoise {
    @Volatile
    var cached: ImageBitmap? = null
        private set

    fun decode(context: Context): ImageBitmap? = runCatchingExceptions {
        context.assets.open("${ChatArtwork.ASSETS_FOLDER}/${ChatArtwork.NOISE_FILE}").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }
        .onFailure { Timber.w(it, "Could not load the wallpaper grain") }
        .getOrNull()
        ?.also { cached = it }
}
