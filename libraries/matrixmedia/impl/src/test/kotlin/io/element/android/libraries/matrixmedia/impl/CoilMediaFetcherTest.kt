/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrixmedia.impl

import android.graphics.Bitmap
import coil3.fetch.SourceFetchResult
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.test.media.FakeMatrixMediaLoader
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.annotation.GraphicsMode
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoilMediaFetcherTest : RobolectricTest() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `a downloaded file within the limit is returned`() = runTest {
        val fetcher = aFetcher(aFile(size = 1_000), maxBytes = 1_000)

        assertThat(fetcher.fetch()).isInstanceOf(SourceFetchResult::class.java)
    }

    @Test
    fun `a downloaded file over the limit fails to load`() = runTest {
        val fetcher = aFetcher(aFile(size = 1_001), maxBytes = 1_000)

        val error = runCatchingExceptions { fetcher.fetch() }.exceptionOrNull()
        assertThat(error).isInstanceOf(MediaTooLargeException::class.java)
    }

    @Test
    fun `an image within the pixel limit is returned`() = runTest {
        val fetcher = aFetcher(aPng(width = 4096, height = 1), maxPixelEdge = 4096)

        assertThat(fetcher.fetch()).isInstanceOf(SourceFetchResult::class.java)
    }

    @Test
    fun `an image over the pixel limit or without a readable size fails to load`() = runTest {
        val svg = temporaryFolder.newFile().apply { writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/>""") }
        listOf(aPng(width = 1, height = 4097), svg).forEach { file ->
            val error = runCatchingExceptions { aFetcher(file, maxPixelEdge = 4096).fetch() }.exceptionOrNull()
            assertThat(error).isInstanceOf(MediaTooLargeException::class.java)
        }
    }

    private fun aFile(size: Int): File = temporaryFolder.newFile().apply { writeBytes(ByteArray(size)) }

    private fun aPng(width: Int, height: Int): File = temporaryFolder.newFile().apply {
        outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun aFetcher(file: File, maxBytes: Long? = null, maxPixelEdge: Int? = null): CoilMediaFetcher {
        val mediaLoader = FakeMatrixMediaLoader().apply { path = file.path }
        val kind = MediaRequestData.Kind.File("photo", "image/jpeg", maxBytes = maxBytes, maxPixelEdge = maxPixelEdge)
        return CoilMediaFetcher(
            mediaLoader = mediaLoader,
            mediaData = MediaRequestData(MediaSource("mxc://gua.global/AbCdEf"), kind),
        )
    }
}
