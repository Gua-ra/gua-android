/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.media

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.media.ImageInfo
import org.junit.Test
import org.matrix.rustcomponents.sdk.ImageInfo as RustImageInfo

class ImageInfoMappingTest {
    @Test
    fun `an animated image keeps its flag both ways`() {
        val info = ImageInfo(
            height = 280,
            width = 498,
            mimetype = "image/webp",
            size = 900_000,
            thumbnailInfo = null,
            thumbnailSource = null,
            blurhash = null,
            isAnimated = true,
        )

        val rustInfo = info.map()

        assertThat(rustInfo.isAnimated).isTrue()
        assertThat(rustInfo.map()).isEqualTo(info)
    }

    @Test
    fun `an image that does not say whether it is animated stays unknown`() {
        val rustInfo = RustImageInfo(
            height = null,
            width = null,
            mimetype = "image/png",
            size = null,
            thumbnailInfo = null,
            thumbnailSource = null,
            blurhash = null,
            isAnimated = null,
        )

        assertThat(rustInfo.map().isAnimated).isNull()
    }
}
