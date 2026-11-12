/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StickerDisplaySizeTest {
    @Test
    fun `a sticker shows at its info size`() {
        assertThat(stickerDisplaySize(width = 128, height = 96)).isEqualTo(DpSize(128.dp, 96.dp))
    }

    @Test
    fun `a sticker larger than 160 is fitted within 160 by 160`() {
        assertThat(stickerDisplaySize(width = 256, height = 128)).isEqualTo(DpSize(160.dp, 80.dp))
        assertThat(stickerDisplaySize(width = 200, height = 400)).isEqualTo(DpSize(80.dp, 160.dp))
    }

    @Test
    fun `a sticker without a size shows at 160`() {
        assertThat(stickerDisplaySize(width = null, height = 100)).isEqualTo(DpSize(160.dp, 160.dp))
        assertThat(stickerDisplaySize(width = 0, height = 0)).isEqualTo(DpSize(160.dp, 160.dp))
    }
}
