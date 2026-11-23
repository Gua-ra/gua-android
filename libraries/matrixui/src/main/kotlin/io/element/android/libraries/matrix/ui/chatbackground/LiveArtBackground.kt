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
import androidx.compose.ui.unit.LayoutDirection
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

internal const val LIVE_STILL_WIDTH = 1290f
internal const val LIVE_STILL_HEIGHT = 2796f

/** The shaders' default send-pulse spot: a share of the width from the leading edge and of the height from the bottom. */
private const val PULSE_SPOT_X = 0.83f
private const val PULSE_SPOT_Y = 0.113f

/** Every live shader's uniforms, with their float counts. */
internal val LIVE_WALLPAPER_UNIFORMS = mapOf(
    "u_res" to 2,
    "u_time" to 1,
    "u_dark" to 1,
    "u_pulse" to 1,
    "u_tilt" to 2,
    "u_origin" to 2,
)

/**
 * Shows [still] until the shader has drawn its first frame, and whenever [motion] asks for it.
 * With a [parallax] the shader applies the tilt itself, so the live view never moves.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun LiveArt(
    artwork: ChatArtwork,
    cues: LiveWallpaperCues,
    motion: WallpaperMotion,
    visibleArea: LiveWallpaperVisibleArea?,
    parallax: WallpaperParallax?,
    still: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Remembered here rather than in the layer, so the count the screen opened with stays the baseline when the layer is replaced.
    val sends = rememberSaveable(saver = LiveWallpaperSendPulse.Saver) { LiveWallpaperSendPulse(seenCount = cues.sendCount) }
    SideEffect { sends.onSendCount(cues.sendCount, System.nanoTime()) }
    val shader by produceState<LiveWallpaperShader?>(initialValue = null, artwork) {
        // Test harnesses that stop infinite animations never get the shader, so tests see the still.
        if (coroutineContext[InfiniteAnimationPolicy] != null) return@produceState
        value = withContext(Dispatchers.Default) { LiveWallpaperShaders.get(context, artwork) }
    }
    val readyShader = shader
    if (readyShader == null) {
        still()
    } else {
        RunningLiveArt(
            shader = readyShader,
            artwork = artwork,
            motion = motion,
            sends = sends,
            visibleArea = visibleArea,
            parallax = parallax,
            still = still,
        )
    }
}

internal fun liveArtShowsStill(motion: WallpaperMotion, hasDrawn: Boolean): Boolean {
    return motion == WallpaperMotion.Still || !hasDrawn
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun RunningLiveArt(
    shader: LiveWallpaperShader,
    artwork: ChatArtwork,
    motion: WallpaperMotion,
    sends: LiveWallpaperSendPulse,
    visibleArea: LiveWallpaperVisibleArea?,
    parallax: WallpaperParallax?,
    still: @Composable () -> Unit,
) {
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
                parallax = parallax,
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
    parallax: WallpaperParallax?,
    onDraw: () -> Unit,
) {
    // A new clock each time the shader replaces the still, so its first frame is the still again.
    val clock = remember { LiveWallpaperClock(stillTime) }
    val isDark = !ElementTheme.isLightTheme
    val sensor = parallax?.sensor
    LaunchedEffect(clock, animates, sensor) {
        if (!animates) {
            clock.hold()
            return@LaunchedEffect
        }
        clock.resume()
        val watchdog = WallpaperFrameWatchdog()
        while (isActive) {
            val drawnFrameNanos = withInfiniteAnimationFrameNanos { frameTimeNanos ->
                val isDrawn = clock.onFrame(frameTimeNanos, sends, tilt = sensor?.value ?: Offset.Zero)
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
    val shift = (parallax?.shift ?: 0).toFloat()
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
            // The live view's own grain, which stays put; the still's grain moves with the still.
            .wallpaperGrain(rememberWallpaperGrain())
            // An offscreen layer caches the frame, so the shader runs once per wallpaper frame, not on every redraw while the timeline scrolls.
            .graphicsLayer {
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawBehind {
                // The frame depends only on the canvas; the composer and the keyboard move only the pulse's spot.
                val frame = liveWallpaperFrame(size, anchor, shift)
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
                    isRtl = layoutDirection == LayoutDirection.Rtl,
                )
                drawLiveWallpaper(shader, frame = frame, origin = origin, time = clock.time, pulse = clock.pulse, tilt = clock.tilt, isDark = isDark)
                onDraw()
            }
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawLiveWallpaper(
    shader: LiveWallpaperShader,
    frame: Rect,
    origin: Offset,
    time: Float,
    pulse: Float,
    tilt: Offset,
    isDark: Boolean,
) {
    with(shader.runtimeShader) {
        // The brush covers the whole frame, and fragCoord is relative to its top left, so u_res is the frame's size.
        setFloatUniform("u_res", frame.width, frame.height)
        setFloatUniform("u_origin", origin.x, origin.y)
        setFloatUniform("u_time", time)
        setFloatUniform("u_dark", if (isDark) 1f else 0f)
        setFloatUniform("u_pulse", pulse)
        setFloatUniform("u_tilt", tilt.x, tilt.y)
    }
    translate(left = frame.left, top = frame.top) {
        drawRect(shader.brush, size = frame.size)
    }
}

/**
 * The still's full frame over [viewSize] grown by [shift] on every side, cropped around [anchor] as the still is. The shaders scale with
 * `u_res`, so drawing over this frame rather than the view keeps the first live frame identical to the still on any screen shape.
 */
internal fun liveWallpaperFrame(viewSize: Size, anchor: UnitPoint, shift: Float = 0f): Rect {
    val frame = wallpaperStillRect(
        viewWidth = viewSize.width.toDouble(),
        viewHeight = viewSize.height.toDouble(),
        anchorX = anchor.x.toDouble(),
        anchorY = anchor.y.toDouble(),
        shift = shift.toDouble(),
    )
    return Rect(Offset(frame.left.toFloat(), frame.top.toFloat()), Size(frame.width.toFloat(), frame.height.toFloat()))
}

/**
 * The shader's `u_origin`: the default send-pulse spot taken on the visible chat area rather than on [frame], in frame pixels with y
 * measured from the frame's bottom. [visibleTop] and [visibleBottom] are canvas pixels from its top; without them the area starts at the
 * canvas's top and ends [bottomInset] above its bottom. Under [isRtl] the spot is mirrored, as outgoing bubbles are.
 */
internal fun liveWallpaperPulseOrigin(
    canvasSize: Size,
    frame: Rect,
    visibleTop: Float?,
    visibleBottom: Float?,
    bottomInset: Float,
    isRtl: Boolean = false,
): Offset {
    // An area reported past the canvas, such as one starting under the top bar, is cut to the canvas.
    val top = (visibleTop ?: 0f).coerceIn(0f, canvasSize.height)
    val bottom = (visibleBottom ?: canvasSize.height - bottomInset).coerceIn(top, canvasSize.height)
    return Offset(
        x = canvasSize.width * (if (isRtl) 1f - PULSE_SPOT_X else PULSE_SPOT_X) - frame.left,
        y = frame.bottom - (bottom - (bottom - top) * PULSE_SPOT_Y),
    )
}

/** Each draw sets the uniforms just before drawing, so screens can share one shader. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal data class LiveWallpaperShader(val runtimeShader: RuntimeShader, val brush: ShaderBrush)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object LiveWallpaperShaders {
    private val shaders = ConcurrentHashMap<ChatArtwork, LiveWallpaperShader>()

    /** Compiled on first use. Null if that fails; a failure is logged once and never retried. */
    fun get(context: Context, artwork: ChatArtwork): LiveWallpaperShader? {
        shaders[artwork]?.let { return it }
        val file = artwork.shaderFile ?: return null
        if (!LiveWallpaperSession.isAvailable(artwork)) return null
        return runCatchingExceptions {
            val source = context.assets.open("${ChatArtwork.ASSETS_FOLDER}/$file").bufferedReader().use { it.readText() }
            val runtimeShader = RuntimeShader(source)
            LiveWallpaperShader(runtimeShader, ShaderBrush(runtimeShader))
        }
            .onFailure { if (LiveWallpaperSession.reportUnavailable(artwork)) Timber.w(it, "Could not create the live wallpaper %s", artwork.id) }
            .getOrNull()
            ?.let { shaders.putIfAbsent(artwork, it) ?: it }
    }
}
