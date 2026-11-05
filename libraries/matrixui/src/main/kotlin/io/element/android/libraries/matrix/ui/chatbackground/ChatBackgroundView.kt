/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.androidutils.system.areAnimationsEnabled
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundFill
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.designsystem.chatbackground.isIncreasedContrastEnabled
import io.element.android.libraries.designsystem.components.blurhash.BlurHashImage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import kotlin.math.max
import kotlin.math.roundToInt

private const val CROSSFADE_DURATION_MS = 200
private const val MAX_DECODED_EDGE_PX = 2048
private const val INCREASED_CONTRAST_MIN_DIM = 0.5f
private const val INCREASED_CONTRAST_LIGHT_SCRIM = 0.06f

@Composable
fun rememberChatBackgroundTransition(background: ChatBackground): Transition<ChatBackground> {
    return updateTransition(targetState = background, label = "chat-background")
}

/** True while [transition] shows a background, including while it fades one in or out. */
val Transition<ChatBackground>.isShowingBackground: Boolean
    get() = currentState != ChatBackground.None || targetState != ChatBackground.None

/** [ChatBackground.None] draws the default canvas. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ChatBackgroundView(
    transition: Transition<ChatBackground>,
    modifier: Modifier = Modifier,
    isLight: Boolean = ElementTheme.isLightTheme,
) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) { context.areAnimationsEnabled() }
    val isIncreasedContrast = isIncreasedContrastEnabled()
    transition.Crossfade(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics {},
        animationSpec = if (animationsEnabled) tween(CROSSFADE_DURATION_MS) else snap(),
    ) { background ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (background) {
                ChatBackground.None -> Box(Modifier.fillMaxSize().background(ElementTheme.colors.bgCanvasDefault))
                is ChatBackground.Preset -> Box(Modifier.fillMaxSize().drawChatBackgroundFill(background.preset.fill(isLight)))
                is ChatBackground.Photo -> PhotoBackground(background, isLight = isLight, isIncreasedContrast = isIncreasedContrast)
            }
            if (isLight && isIncreasedContrast && background != ChatBackground.None) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = INCREASED_CONTRAST_LIGHT_SCRIM)))
            }
        }
    }
}

@Composable
fun ChatBackgroundView(
    background: ChatBackground,
    modifier: Modifier = Modifier,
    isLight: Boolean = ElementTheme.isLightTheme,
) {
    ChatBackgroundView(transition = rememberChatBackgroundTransition(background), modifier = modifier, isLight = isLight)
}

fun Modifier.drawChatBackgroundFill(fill: ChatBackgroundFill): Modifier = drawBehind { drawFill(fill) }

private fun DrawScope.drawFill(fill: ChatBackgroundFill) {
    when (fill) {
        is ChatBackgroundFill.Solid -> drawRect(Color(fill.color))
        is ChatBackgroundFill.LinearGradient -> drawRect(
            Brush.linearGradient(
                colorStops = fill.stops.map { it.offset to Color(it.color) }.toTypedArray(),
                start = Offset(fill.start.x * size.width, fill.start.y * size.height),
                end = Offset(fill.end.x * size.width, fill.end.y * size.height),
            )
        )
        is ChatBackgroundFill.Aurora -> {
            val longestSide = max(size.width, size.height)
            drawRect(Color(fill.base))
            drawGlow(Color(fill.glowA), Offset(fill.glowACenter.x * size.width, fill.glowACenter.y * size.height), fill.glowARadius * longestSide)
            drawGlow(Color(fill.glowB), Offset(fill.glowBCenter.x * size.width, fill.glowBCenter.y * size.height), fill.glowBRadius * longestSide)
        }
    }
}

private fun DrawScope.drawGlow(color: Color, center: Offset, radius: Float) {
    drawRect(Brush.radialGradient(colors = listOf(color, color.copy(alpha = 0f)), center = center, radius = radius))
}

@Composable
private fun PhotoBackground(
    photo: ChatBackground.Photo,
    isLight: Boolean,
    isIncreasedContrast: Boolean,
) {
    var isLoaded by remember(photo.url) { mutableStateOf(false) }
    var hasFailed by remember(photo.url) { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt().coerceIn(1, MAX_DECODED_EDGE_PX)
        val heightPx = with(density) { maxHeight.toPx() }.roundToInt().coerceIn(1, MAX_DECODED_EDGE_PX)
        if (!isLoaded) {
            Box(Modifier.fillMaxSize().background(ElementTheme.colors.bgCanvasDefault))
            if (!hasFailed) {
                BlurHashImage(blurHash = photo.blurhash, contentScale = ContentScale.Crop)
            }
        }
        if (!hasFailed) {
            val context = LocalContext.current
            val request = remember(photo.url, widthPx, heightPx) {
                // Downloaded to a file, not into memory, so Coil decodes it downsampled; the declared size is not trusted.
                val kind = MediaRequestData.Kind.File(fileName = "chat-background", mimeType = photo.mimetype, maxBytes = ChatBackground.Photo.MAX_SIZE)
                ImageRequest.Builder(context)
                    .data(MediaRequestData(MediaSource(photo.url), kind))
                    .size(widthPx, heightPx)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = photo.alignment(),
                onSuccess = { isLoaded = true },
                onError = { hasFailed = true },
            )
        }
        val dim = (photo.dim / 100f).let { if (isIncreasedContrast) max(it, INCREASED_CONTRAST_MIN_DIM) else it }
        if (!isLight && !hasFailed && dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}

fun ChatBackground.Photo.alignment() = chatBackgroundPhotoAlignment(alignX, alignY)

/**
 * With [ContentScale.Crop], a photo overflowing its frame by `o` on an axis is offset by `-o × align / 1000`, as on iOS.
 * Never mirrored for right-to-left layouts.
 */
fun chatBackgroundPhotoAlignment(alignX: Int, alignY: Int) = BiasAbsoluteAlignment(
    horizontalBias = alignX / 1000f * 2 - 1,
    verticalBias = alignY / 1000f * 2 - 1,
)

@PreviewsDayNight
@Composable
internal fun ChatBackgroundViewPreview() = ElementPreview {
    val backgrounds = listOf(ChatBackground.None) +
        ChatBackgroundPreset.entries.map(ChatBackground::Preset) +
        listOf(aChatBackgroundPhoto(dim = 0), aChatBackgroundPhoto(dim = ChatBackground.Photo.MAX_DIM))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(4.dp)) {
        backgrounds.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { background ->
                    ChatBackgroundView(background = background, modifier = Modifier.size(width = 64.dp, height = 128.dp))
                }
            }
        }
    }
}

fun aChatBackgroundPhoto(
    dim: Int = ChatBackground.Photo.DEFAULT_DIM,
    blurhash: String? = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
) = ChatBackground.Photo(
    url = "mxc://gua.global/AbCdEf",
    mimetype = "image/jpeg",
    width = 1536,
    height = 2048,
    size = 412_337,
    blurhash = blurhash,
    dim = dim,
)
