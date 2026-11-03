/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrixmedia.impl

import coil3.fetch.SourceFetchResult
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.test.media.FakeMatrixMediaLoader
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoilMediaFetcherTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `a downloaded file within the limit is returned`() = runTest {
        val fetcher = aFetcher(fileSize = 1_000, maxBytes = 1_000)

        assertThat(fetcher.fetch()).isInstanceOf(SourceFetchResult::class.java)
    }

    @Test
    fun `a downloaded file over the limit fails to load`() = runTest {
        val fetcher = aFetcher(fileSize = 1_001, maxBytes = 1_000)

        val error = runCatchingExceptions { fetcher.fetch() }.exceptionOrNull()
        assertThat(error).isInstanceOf(MediaTooLargeException::class.java)
    }

    private fun aFetcher(fileSize: Int, maxBytes: Long): CoilMediaFetcher {
        val file = temporaryFolder.newFile().apply { writeBytes(ByteArray(fileSize)) }
        val mediaLoader = FakeMatrixMediaLoader().apply { path = file.path }
        return CoilMediaFetcher(
            mediaLoader = mediaLoader,
            mediaData = MediaRequestData(MediaSource("mxc://gua.global/AbCdEf"), MediaRequestData.Kind.File("photo", "image/jpeg", maxBytes)),
        )
    }
}
