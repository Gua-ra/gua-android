/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import androidx.test.core.app.ApplicationProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.useExistingImageAsPlaceholder
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatBackgroundArtTest : RobolectricTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a still is halved only while it still covers the frame`() {
        assertThat(wallpaperSampleSize(1290, 2796, 231, 410)).isEqualTo(4)
        assertThat(wallpaperSampleSize(1290, 2796, 645, 1398)).isEqualTo(2)
        assertThat(wallpaperSampleSize(1290, 2796, 646, 1398)).isEqualTo(1)
        assertThat(wallpaperSampleSize(1290, 2796, 1080, 2000)).isEqualTo(1)
        assertThat(wallpaperSampleSize(1290, 2796, 2048, 1000)).isEqualTo(1)
        assertThat(wallpaperSampleSize(0, 0, 231, 410)).isEqualTo(1)
    }

    @Test
    fun `in a preview every artwork's still decodes in place in both appearances, covering the frame`() {
        ChatArtwork.entries.flatMap { listOf(it.lightFile, it.darkFile) }.forEach { file ->
            val still = decodeWallpaperStill(context, file, widthPx = 231, heightPx = 410)
            assertWithMessage(file).that(still).isNotNull()
            assertWithMessage(file).that(still!!.width).isIn(231 until 1290)
            assertWithMessage(file).that(still.height).isIn(410 until 2796)
        }
    }

    @Test
    fun `a still that cannot be read decodes to nothing, so the fallback shows`() {
        assertThat(decodeWallpaperStill(context, "missing-light.webp", widthPx = 231, heightPx = 410)).isNull()
    }

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun `a new size or appearance keeps the previous still on screen while its own loads`() {
        assertThat(wallpaperStillRequest(context, ChatArtwork.Silk.darkFile, widthPx = 231, heightPx = 410).useExistingImageAsPlaceholder).isTrue()
    }

    @Test
    fun `the grain uses soft light from API 29, and overlay below where soft light would draw as a plain veil`() {
        assertThat(wallpaperGrainStyle(Build.VERSION_CODES.Q)).isEqualTo(WallpaperGrainStyle(BlendMode.Softlight, alpha = 0.08f))
        assertThat(wallpaperGrainStyle(Build.VERSION_CODES.P)).isEqualTo(WallpaperGrainStyle(BlendMode.Overlay, alpha = 0.04f))
    }
}
