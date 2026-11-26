/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.keyboard

import io.element.android.features.expressions.impl.sticker.StickerImageEncoder
import io.element.android.libraries.core.mimetype.MimeTypes
import kotlin.math.max

enum class KeyboardRoute {
    Gif,
    Sticker,

    /** A sticker only when the image turns out to be small and partly transparent. */
    StickerIfTransparent,
    Unchanged,
}

internal object KeyboardContentRules {
    const val WHATSAPP_STICKER = "image/webp.wasticker"
    const val MAX_KEYBOARD_STICKER_SIDE = 1024
    const val MAX_STICKER_SIDE = 512

    fun route(mimeType: String?, isFromInputMethod: Boolean): KeyboardRoute = when {
        mimeType == MimeTypes.Gif -> KeyboardRoute.Gif
        mimeType == WHATSAPP_STICKER -> KeyboardRoute.Sticker
        isFromInputMethod && mimeType?.startsWith("image/") == true -> KeyboardRoute.StickerIfTransparent
        else -> KeyboardRoute.Unchanged
    }

    fun isSticker(facts: ImageFacts): Boolean =
        facts.hasTransparency && max(facts.width, facts.height) <= MAX_KEYBOARD_STICKER_SIDE

    /** An animated file is sent as it is: re-encoding would keep only its first frame. */
    fun needsReencoding(facts: ImageFacts, size: Long): Boolean =
        !facts.isAnimated && (max(facts.width, facts.height) > MAX_STICKER_SIDE || size > StickerImageEncoder.MAX_FILE_SIZE)

    fun uploadMimeType(mimeType: String): String = if (mimeType == WHATSAPP_STICKER) MimeTypes.WebP else mimeType
}
