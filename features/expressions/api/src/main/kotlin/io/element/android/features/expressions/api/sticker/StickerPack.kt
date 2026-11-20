/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import io.element.android.features.expressions.api.array
import io.element.android.features.expressions.api.obj
import io.element.android.features.expressions.api.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class StickerPack(
    val eventType: String,
    val stateKey: String,
    val displayName: String,
    val avatarUrl: String?,
    val stickers: List<PackSticker>,
)

data class PackSticker(
    val shortcode: String,
    val image: StickerImage,
)

object StickerPackCodec {
    const val EVENT_TYPE = "m.room.image_pack"
    const val UNSTABLE_EVENT_TYPE = "im.ponies.room_emotes"
    val EVENT_TYPES = listOf(EVENT_TYPE, UNSTABLE_EVENT_TYPE)

    private const val STICKER_USAGE = "sticker"

    fun decode(
        eventType: String,
        stateKey: String,
        content: JsonObject,
        roomName: String,
        roomAvatarUrl: String?,
    ): StickerPack {
        val pack = content.obj("pack")
        val packIsForStickers = pack?.array("usage").allowsStickers()
        val stickers = content.obj("images").orEmpty()
            .mapNotNull { (shortcode, element) ->
                val image = StickerImage.fromJson(element, bodyFallback = shortcode) ?: return@mapNotNull null
                // Only the unstable format lets an image override its pack's usage.
                val imageUsage = (element as JsonObject).array("usage")?.takeIf { eventType == UNSTABLE_EVENT_TYPE }
                val isSticker = if (imageUsage != null) imageUsage.allowsStickers() else packIsForStickers
                PackSticker(shortcode, image).takeIf { isSticker }
            }
            .sortedBy { it.shortcode }
        return StickerPack(
            eventType = eventType,
            stateKey = stateKey,
            displayName = pack?.string("display_name")?.takeIf { it.isNotBlank() } ?: roomName,
            avatarUrl = pack?.string("avatar_url")?.takeIf { it.startsWith("mxc://") } ?: roomAvatarUrl,
            stickers = stickers,
        )
    }

    private fun JsonArray?.allowsStickers(): Boolean {
        if (isNullOrEmpty()) return true
        return any { (it as? JsonPrimitive)?.takeIf { usage -> usage.isString }?.content == STICKER_USAGE }
    }
}
