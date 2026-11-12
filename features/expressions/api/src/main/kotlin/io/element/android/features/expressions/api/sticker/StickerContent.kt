/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import io.element.android.features.expressions.api.sortedKeys
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class StickerSize(val width: Int, val height: Int)

object StickerContent {
    const val EVENT_TYPE = "m.sticker"
    const val MAX_DISPLAY_SIZE = 256
    const val DEFAULT_BODY = "Sticker"

    /** Half the pixel size, scaled down to fit within [MAX_DISPLAY_SIZE]. */
    fun displaySize(pixelWidth: Int, pixelHeight: Int): StickerSize {
        val halfWidth = pixelWidth / 2.0
        val halfHeight = pixelHeight / 2.0
        val scale = min(1.0, MAX_DISPLAY_SIZE / max(halfWidth, halfHeight))
        return StickerSize(
            width = (halfWidth * scale).roundToInt().coerceAtLeast(1),
            height = (halfHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }

    fun image(
        url: String,
        body: String,
        pixelWidth: Int,
        pixelHeight: Int,
        mimetype: String,
        size: Long,
        isAnimated: Boolean,
    ): StickerImage {
        val displaySize = displaySize(pixelWidth, pixelHeight)
        return StickerImage(
            url = url,
            body = body,
            info = buildJsonObject {
                put("h", displaySize.height)
                if (isAnimated) put("is_animated", true)
                put("mimetype", mimetype)
                put("size", size)
                put("w", displaySize.width)
            },
        )
    }

    fun encode(image: StickerImage, relation: StickerRelation): String {
        val content = buildJsonObject {
            put("body", image.body?.takeIf { it.isNotBlank() } ?: DEFAULT_BODY)
            put("info", image.info ?: JsonObject(emptyMap()))
            put("url", image.url)
            when (relation) {
                StickerRelation.None -> Unit
                is StickerRelation.Reply -> putJsonObject("m.relates_to") {
                    putJsonObject("m.in_reply_to") { put("event_id", relation.eventId.value) }
                }
                is StickerRelation.Thread -> putJsonObject("m.relates_to") {
                    put("event_id", relation.rootId.value)
                    put("is_falling_back", true)
                    putJsonObject("m.in_reply_to") { put("event_id", relation.latestEventId.value) }
                    put("rel_type", "m.thread")
                }
            }
        }
        return content.sortedKeys().toString()
    }
}
