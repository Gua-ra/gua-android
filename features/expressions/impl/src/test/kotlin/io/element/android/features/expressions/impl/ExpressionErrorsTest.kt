/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.gif.GifSendException
import io.element.android.features.expressions.api.gif.GifsUnavailableException
import io.element.android.features.expressions.api.sticker.StickerLimitReachedException
import io.element.android.features.expressions.api.sticker.StickerSendException
import org.junit.Test
import java.io.IOException

class ExpressionErrorsTest {
    @Test
    fun `each failure maps to its message`() {
        assertThat(StickerLimitReachedException().expressionErrorMessage()).isEqualTo(R.string.gua_sticker_limit_reached)
        assertThat(StickerSendException.UploadFailed(IOException()).expressionErrorMessage()).isEqualTo(R.string.gua_sticker_send_failed)
        assertThat(StickerSendException.SendFailed(IOException()).expressionErrorMessage()).isEqualTo(R.string.gua_sticker_send_failed)
        assertThat(GifSendException.DownloadFailed(IOException()).expressionErrorMessage()).isEqualTo(R.string.gua_gif_send_failed)
        assertThat(GifsUnavailableException().expressionErrorMessage()).isEqualTo(R.string.gua_gifs_unavailable)
        assertThat(IOException().expressionErrorMessage()).isNull()
    }

    @Test
    fun `a failed GIF request says GIFs could not load, unless this build has none`() {
        assertThat(IOException().gifLoadErrorMessage()).isEqualTo(R.string.gua_gifs_load_failed)
        assertThat(GifsUnavailableException().gifLoadErrorMessage()).isEqualTo(R.string.gua_gifs_unavailable)
    }
}
