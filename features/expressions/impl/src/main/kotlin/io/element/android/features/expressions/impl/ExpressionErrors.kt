/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl

import androidx.annotation.StringRes
import io.element.android.features.expressions.api.gif.GifSendException
import io.element.android.features.expressions.api.gif.GifsUnavailableException
import io.element.android.features.expressions.api.sticker.StickerLimitReachedException
import io.element.android.features.expressions.api.sticker.StickerSendException

@StringRes
fun Throwable.expressionErrorMessage(): Int? = when (this) {
    is StickerLimitReachedException -> R.string.gua_sticker_limit_reached
    is StickerSendException -> R.string.gua_sticker_send_failed
    is GifsUnavailableException -> R.string.gua_gifs_unavailable
    is GifSendException -> R.string.gua_gif_send_failed
    else -> null
}

@StringRes
fun Throwable.gifLoadErrorMessage(): Int = expressionErrorMessage() ?: R.string.gua_gifs_load_failed
