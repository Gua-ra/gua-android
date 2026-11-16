/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.chatbackground.api.UnitPoint
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LiveWallpaperVisibleAreaTest : RobolectricTest() {
    @Test
    fun `a chat screen's area runs from its top bar to its composer and follows the keyboard, while the canvas stays put`() =
        runAndroidComposeUiTest<ComponentActivity> {
            val area = LiveWallpaperVisibleArea()
            var composerHeight by mutableStateOf(56.dp)
            var keyboardHeight by mutableStateOf(0.dp)
            var canvas = Rect.Zero
            setContent {
                Box(Modifier.fillMaxSize()) {
                    // Like the timeline's: below the top bar, outside the keyboard padding.
                    Box(Modifier.fillMaxSize().padding(top = TOP_BAR).onGloballyPositioned { canvas = it.boundsInRoot() })
                    Column(Modifier.fillMaxSize().padding(bottom = keyboardHeight)) {
                        Spacer(Modifier.height(TOP_BAR))
                        Box(Modifier.weight(1f).fillMaxWidth().liveWallpaperChatArea(area))
                        Box(Modifier.height(composerHeight).fillMaxWidth())
                    }
                }
            }
            val closedCanvas = runOnIdle { canvas }
            assertChat(area, closedCanvas, top = 0.dp, aboveCanvasBottom = 56.dp)

            composerHeight = 120.dp
            assertChat(area, closedCanvas, top = 0.dp, aboveCanvasBottom = 120.dp)

            keyboardHeight = 260.dp
            assertChat(area, closedCanvas, top = 0.dp, aboveCanvasBottom = 380.dp)
            runOnIdle { assertThat(canvas).isEqualTo(closedCanvas) }
        }

    @Test
    fun `a preview's area ends at the top of its controls`() = runAndroidComposeUiTest<ComponentActivity> {
        val area = LiveWallpaperVisibleArea()
        var controlsHeight by mutableStateOf(180.dp)
        var canvas = Rect.Zero
        setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(top = TOP_BAR).onGloballyPositioned { canvas = it.boundsInRoot() })
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(controlsHeight).liveWallpaperChatAreaEnd(area))
            }
        }
        val previewCanvas = runOnIdle { canvas }
        runOnIdle { assertThat(area.top).isNull() }
        assertChat(area, previewCanvas, top = null, aboveCanvasBottom = 180.dp)
        controlsHeight = 220.dp
        assertChat(area, previewCanvas, top = null, aboveCanvasBottom = 220.dp)
    }

    @Test
    fun `the spot lands in the chat above the composer and the keyboard`() = runAndroidComposeUiTest<ComponentActivity> {
        val area = LiveWallpaperVisibleArea()
        var canvas = Rect.Zero
        setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(top = TOP_BAR).onGloballyPositioned { canvas = it.boundsInRoot() })
                Column(Modifier.fillMaxSize().padding(bottom = 200.dp)) {
                    Spacer(Modifier.height(TOP_BAR))
                    Box(Modifier.weight(1f).fillMaxWidth().liveWallpaperChatArea(area))
                    Box(Modifier.height(56.dp).fillMaxWidth())
                }
            }
        }
        runOnIdle {
            val chat = liveWallpaperCanvasArea(canvasRootTop = canvas.top, area = area, systemBottomInset = 0f, spaceBelowCanvas = 0f)
            val areaTop = area.top!!
            val areaBottom = area.bottom!!
            ChatArtwork.entries.filter { it.isLive }.forEach { artwork ->
                val frame = liveWallpaperFrame(canvas.size, artwork.anchor)
                val origin = liveWallpaperPulseOrigin(canvas.size, frame, chat.top, chat.bottom, chat.bottomInset)
                val spotInRoot = canvas.top + frame.bottom - origin.y
                assertThat(spotInRoot).isIn(Range.closed(areaTop, areaBottom))
                assertThat(spotInRoot).isWithin(0.01f).of(areaBottom - (areaBottom - areaTop) * 0.113f)
            }
        }
    }

    @Test
    fun `the area's edges are taken from the canvas's top`() {
        val area = LiveWallpaperVisibleArea().apply {
            top = 240f
            bottom = 1900f
        }
        assertThat(canvasArea(canvasRootTop = 0f, area)).isEqualTo(LiveWallpaperCanvasArea(top = 240f, bottom = 1900f, bottomInset = 0f))
        assertThat(canvasArea(canvasRootTop = 240f, area)).isEqualTo(LiveWallpaperCanvasArea(top = 0f, bottom = 1660f, bottomInset = 0f))
        // A canvas that starts below the chat, such as one under a taller top bar.
        assertThat(canvasArea(canvasRootTop = 300f, area)).isEqualTo(LiveWallpaperCanvasArea(top = -60f, bottom = 1600f, bottomInset = 0f))
    }

    @Test
    fun `a preview's area has only a bottom, taken from the canvas's top`() {
        val area = LiveWallpaperVisibleArea().apply { bottom = 1800f }
        assertThat(canvasArea(canvasRootTop = 180f, area)).isEqualTo(LiveWallpaperCanvasArea(top = null, bottom = 1620f, bottomInset = 0f))
    }

    @Test
    fun `without a reported area the canvas keeps its own edges`() {
        val expected = LiveWallpaperCanvasArea(top = null, bottom = null, bottomInset = 0f)
        assertThat(canvasArea(canvasRootTop = 180f, area = null)).isEqualTo(expected)
        assertThat(canvasArea(canvasRootTop = 180f, area = LiveWallpaperVisibleArea())).isEqualTo(expected)
    }

    @Test
    fun `the fallback inset is the part of the system bottom inset the canvas reaches into`() {
        // Reaching the window's bottom, under the navigation bar.
        assertThat(canvasArea(canvasRootTop = 180f, area = null, systemBottomInset = 63f, spaceBelowCanvas = 0f).bottomInset).isEqualTo(63f)
        // Padded for the navigation bar, as the chat screen's canvas is.
        assertThat(canvasArea(canvasRootTop = 180f, area = null, systemBottomInset = 63f, spaceBelowCanvas = 63f).bottomInset).isEqualTo(0f)
        assertThat(canvasArea(canvasRootTop = 180f, area = null, systemBottomInset = 63f, spaceBelowCanvas = 20f).bottomInset).isEqualTo(43f)
        assertThat(canvasArea(canvasRootTop = 180f, area = null, systemBottomInset = 63f, spaceBelowCanvas = 400f).bottomInset).isEqualTo(0f)
    }

    @Test
    fun `a reported bottom wins over the fallback inset`() {
        val area = LiveWallpaperVisibleArea().apply { bottom = 1800f }
        val chat = canvasArea(canvasRootTop = 180f, area, systemBottomInset = 63f, spaceBelowCanvas = 0f)
        val canvas = Size(1080f, 2000f)
        val frame = liveWallpaperFrame(canvas, UnitPoint(0.5f, 0.5f))
        val origin = liveWallpaperPulseOrigin(canvas, frame, chat.top, chat.bottom, chat.bottomInset)
        assertThat(frame.bottom - origin.y).isWithin(0.01f).of(1620f - 1620f * 0.113f)
    }

    private fun canvasArea(
        canvasRootTop: Float,
        area: LiveWallpaperVisibleArea?,
        systemBottomInset: Float = 0f,
        spaceBelowCanvas: Float = 0f,
    ) = liveWallpaperCanvasArea(canvasRootTop = canvasRootTop, area = area, systemBottomInset = systemBottomInset, spaceBelowCanvas = spaceBelowCanvas)

    private fun AndroidComposeUiTest<ComponentActivity>.assertChat(area: LiveWallpaperVisibleArea, canvas: Rect, top: Dp?, aboveCanvasBottom: Dp) {
        runOnIdle {
            val chat = liveWallpaperCanvasArea(canvasRootTop = canvas.top, area = area, systemBottomInset = 0f, spaceBelowCanvas = 0f)
            if (top == null) {
                assertThat(chat.top).isNull()
            } else {
                assertThat(chat.top).isWithin(0.5f).of(with(density) { top.toPx() })
            }
            assertThat(chat.bottom).isWithin(0.5f).of(canvas.height - with(density) { aboveCanvasBottom.toPx() })
        }
    }
}

private val TOP_BAR = 64.dp
