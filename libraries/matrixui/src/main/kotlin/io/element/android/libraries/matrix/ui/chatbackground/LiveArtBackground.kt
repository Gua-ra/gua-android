/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.chatbackground.api.UnitPoint
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** The size the stills are rendered at. */
internal const val LIVE_STILL_WIDTH = 1290f
internal const val LIVE_STILL_HEIGHT = 2796f

/** The shaders' default send-pulse spot, as shares of the width and the height from the bottom-left corner of the area it is taken on. */
private const val PULSE_SPOT_X = 0.83f
private const val PULSE_SPOT_Y = 0.113f

/** The uniforms every live wallpaper shader declares, with their number of floats. */
internal val LIVE_WALLPAPER_UNIFORMS = mapOf(
    "u_res" to 2,
    "u_time" to 1,
    "u_dark" to 1,
    "u_pulse" to 1,
    "u_tilt" to 2,
    "u_origin" to 2,
)

/**
 * A live artwork over its [still]. The still shows until the shader has drawn its first frame, and whenever [rememberWallpaperMotion]
 * asks for it. Under a test harness that stops infinite animations the shader is never created, so tests always see the still.
 * Its send pulse starts in the screen's [visibleArea].
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun LiveArt(
    artwork: ChatArtwork,
    cues: LiveWallpaperCues,
    visibleArea: LiveWallpaperVisibleArea?,
    still: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Here rather than in the layer, so the count the screen opened with is the baseline even when the shader or the motion
    // replaces the layer later.
    val sends = rememberSaveable(saver = LiveWallpaperSendPulse.Saver) { LiveWallpaperSendPulse(seenCount = cues.sendCount) }
    SideEffect { sends.onSendCount(cues.sendCount, System.nanoTime()) }
    val shader by produceState<LiveWallpaperShader?>(initialValue = null, artwork) {
        if (coroutineContext[InfiniteAnimationPolicy] != null) return@produceState
        value = withContext(Dispatchers.Default) { LiveWallpaperShaders.get(context, artwork) }
    }
    val readyShader = shader
    if (readyShader == null) {
        still()
    } else {
        RunningLiveArt(shader = readyShader, artwork = artwork, isCovered = cues.isCovered, sends = sends, visibleArea = visibleArea, still = still)
    }
}

/** Whether a live artwork shows its still: whenever the policy asks for it, and until the shader has drawn its first frame. */
internal fun liveArtShowsStill(motion: WallpaperMotion, hasDrawn: Boolean): Boolean {
    return motion == WallpaperMotion.Still || !hasDrawn
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun RunningLiveArt(
    shader: LiveWallpaperShader,
    artwork: ChatArtwork,
    isCovered: Boolean,
    sends: LiveWallpaperSendPulse,
    visibleArea: LiveWallpaperVisibleArea?,
    still: @Composable () -> Unit,
) {
    val motion = rememberWallpaperMotion(isCovered = isCovered)
    val showsShader = motion != WallpaperMotion.Still
    var hasDrawn by remember(showsShader) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (liveArtShowsStill(motion, hasDrawn)) {
            still()
        }
        if (showsShader) {
            LiveArtLayer(
                shader = shader,
                stillTime = artwork.stillTime,
                anchor = artwork.anchor,
                animates = motion == WallpaperMotion.Animating,
                sends = sends,
                visibleArea = visibleArea,
                onDraw = { hasDrawn = true },
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LiveArtLayer(
    shader: LiveWallpaperShader,
    stillTime: Float,
    anchor: UnitPoint,
    animates: Boolean,
    sends: LiveWallpaperSendPulse,
    visibleArea: LiveWallpaperVisibleArea?,
    onDraw: () -> Unit,
) {
    // A new clock each time the shader replaces the still, so its first frame is the still again.
    val clock = remember { LiveWallpaperClock(stillTime) }
    val isDark = !ElementTheme.isLightTheme
    LaunchedEffect(clock, animates) {
        if (!animates) {
            clock.hold()
            return@LaunchedEffect
        }
        clock.resume()
        val watchdog = WallpaperFrameWatchdog()
        while (isActive) {
            val drawnFrameNanos = withInfiniteAnimationFrameNanos { frameTimeNanos ->
                val isDrawn = clock.onFrame(frameTimeNanos, sends)
                if (isDrawn && watchdog.onFrame(frameTimeNanos) && !LiveWallpaperSession.isOverFrameBudget) {
                    Timber.w("The live wallpaper is too slow on this device; showing stills until the app restarts")
                    LiveWallpaperSession.isOverFrameBudget = true
                }
                if (isDrawn) frameTimeNanos else null
            }
            if (drawnFrameNanos != null) {
                delay(liveWallpaperWakeDelayMillis(drawnFrameNanos, System.nanoTime()))
            }
        }
    }
    val systemBottomInset = WindowInsets.systemBars.getBottom(LocalDensity.current).toFloat()
    var canvasTop by remember { mutableFloatStateOf(0f) }
    var spaceBelowCanvas by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                canvasTop = coordinates.positionInRoot().y
                spaceBelowCanvas = coordinates.findRootCoordinates().size.height - canvasTop - coordinates.size.height
            }
            // An offscreen layer keeps the rendered frame, so the shader runs once per wallpaper frame rather than each time the
            // window redraws over it, such as while the timeline scrolls.
            .graphicsLayer {
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawBehind {
                // The frame depends on the canvas alone, so the composer and the keyboard only ever move the pulse's spot.
                val frame = liveWallpaperFrame(size, anchor)
                val chat = liveWallpaperCanvasArea(
                    canvasRootTop = canvasTop,
                    area = visibleArea,
                    systemBottomInset = systemBottomInset,
                    spaceBelowCanvas = spaceBelowCanvas,
                )
                val origin = liveWallpaperPulseOrigin(
                    canvasSize = size,
                    frame = frame,
                    visibleTop = chat.top,
                    visibleBottom = chat.bottom,
                    bottomInset = chat.bottomInset,
                )
                drawLiveWallpaper(shader, frame = frame, origin = origin, time = clock.time, pulse = clock.pulse, isDark = isDark)
                onDraw()
            }
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawLiveWallpaper(shader: LiveWallpaperShader, frame: Rect, origin: Offset, time: Float, pulse: Float, isDark: Boolean) {
    with(shader.runtimeShader) {
        // The brush covers the whole frame, and fragCoord is relative to its top left, so u_res is the frame's size.
        setFloatUniform("u_res", frame.width, frame.height)
        setFloatUniform("u_origin", origin.x, origin.y)
        setFloatUniform("u_time", time)
        setFloatUniform("u_dark", if (isDark) 1f else 0f)
        setFloatUniform("u_pulse", pulse)
    }
    translate(left = frame.left, top = frame.top) {
        drawRect(shader.brush, size = frame.size)
    }
}

/**
 * The still's whole frame over a view of [viewSize], cropped around [anchor] exactly as the still is. The shaders scale with `u_res`,
 * so drawing over this frame rather than the view keeps the first live frame identical to the still on any screen shape.
 */
internal fun liveWallpaperFrame(viewSize: Size, anchor: UnitPoint): Rect {
    val scale = max(viewSize.width / LIVE_STILL_WIDTH, viewSize.height / LIVE_STILL_HEIGHT)
    val frameSize = Size(LIVE_STILL_WIDTH * scale, LIVE_STILL_HEIGHT * scale)
    val topLeft = Offset((viewSize.width - frameSize.width) * anchor.x, (viewSize.height - frameSize.height) * anchor.y)
    return Rect(topLeft, frameSize)
}

/**
 * The shader's `u_origin` for a canvas of [canvasSize] drawing [frame]: the shaders' default send-pulse spot taken on the visible chat area
 * rather than on the frame, which reaches past it, in the frame's pixels with y measured from its bottom.
 * The visible area spans the canvas's width, from [visibleTop] (else the canvas's top) down to [visibleBottom], in the canvas's pixels
 * measured from its top. Without a [visibleBottom], such as when no composer shows, it ends [bottomInset] above the canvas's bottom.
 */
internal fun liveWallpaperPulseOrigin(
    canvasSize: Size,
    frame: Rect,
    visibleTop: Float?,
    visibleBottom: Float?,
    bottomInset: Float,
): Offset {
    // The chat only shows where the canvas does, so an area reported past it, such as one that starts under the top bar, is cut to it.
    val top = (visibleTop ?: 0f).coerceIn(0f, canvasSize.height)
    val bottom = (visibleBottom ?: canvasSize.height - bottomInset).coerceIn(top, canvasSize.height)
    return Offset(
        x = canvasSize.width * PULSE_SPOT_X - frame.left,
        y = frame.bottom - (bottom - (bottom - top) * PULSE_SPOT_Y),
    )
}

/** One shader per artwork, wrapped once in its brush. Each draw sets its uniforms just before drawing, so screens can share it. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal data class LiveWallpaperShader(val runtimeShader: RuntimeShader, val brush: ShaderBrush)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object LiveWallpaperShaders {
    private val shaders = ConcurrentHashMap<ChatArtwork, LiveWallpaperShader>()

    /**
     * The artwork's shader, read and compiled on first use. Null if that failed, which is logged once, never retried and recorded in
     * [LiveWallpaperSession].
     */
    fun get(context: Context, artwork: ChatArtwork): LiveWallpaperShader? {
        shaders[artwork]?.let { return it }
        val file = artwork.shaderFile ?: return null
        if (!LiveWallpaperSession.isAvailable(artwork)) return null
        return runCatchingExceptions {
            val source = context.assets.open("${ChatArtwork.ASSETS_FOLDER}/$file").bufferedReader().use { it.readText() }
            val runtimeShader = RuntimeShader(source).apply { setFloatUniform("u_tilt", 0f, 0f) }
            LiveWallpaperShader(runtimeShader, ShaderBrush(runtimeShader))
        }
            .onFailure { if (LiveWallpaperSession.reportUnavailable(artwork)) Timber.w(it, "Could not create the live wallpaper %s", artwork.id) }
            .getOrNull()
            ?.let { shaders.putIfAbsent(artwork, it) ?: it }
    }
}
