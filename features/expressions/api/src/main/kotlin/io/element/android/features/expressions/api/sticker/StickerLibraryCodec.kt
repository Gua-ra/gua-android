/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import io.element.android.features.expressions.api.array
import io.element.android.features.expressions.api.long
import io.element.android.features.expressions.api.obj
import io.element.android.features.expressions.api.string
import io.element.android.features.expressions.api.with
import io.element.android.features.expressions.api.withVersion
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads and edits `global.gua.stickers`. Edits work on the stored JSON, so keys this version
 * does not know survive every rewrite.
 */
object StickerLibraryCodec {
    const val EVENT_TYPE = "global.gua.stickers"
    const val MAX_STICKERS = 120
    const val MAX_FAVOURITES = 50
    const val ADDED_KEY = "global.gua.added"

    private val newestFirst = compareBy<OwnSticker, Long?>(nullsLast(reverseOrder())) { it.addedAt }.thenBy { it.shortcode }

    fun decode(content: JsonObject): StickerLibrary = StickerLibrary(
        favourites = content.array("favourites").orEmpty().mapNotNull { StickerImage.fromJson(it) },
        stickers = content.validImages().sortedWith(newestFirst),
    )

    fun add(content: JsonObject, image: StickerImage, now: Long): JsonObject {
        if (content.validImages().size >= MAX_STICKERS) throw StickerLimitReachedException()
        val images = content.obj("images") ?: JsonObject(emptyMap())
        val shortcode = shortcode(now, images.keys)
        val stored = image.toJson().with(ADDED_KEY, JsonPrimitive(now))
        return content.with("images", images.with(shortcode, stored)).withVersion()
    }

    /** Puts [image] first in the favourites, keeping only its body, info and url. */
    fun favourite(content: JsonObject, image: StickerImage, limit: Int = MAX_FAVOURITES): JsonObject {
        val others = content.array("favourites").orEmpty().filterNot { it.hasUrl(image.url) }
        val favourites = listOf(image.toJson()) + others
        return content.with("favourites", JsonArray(favourites.take(limit))).withVersion()
    }

    fun unfavourite(content: JsonObject, url: String): JsonObject {
        val favourites = content.array("favourites") ?: return content.withVersion()
        return content.with("favourites", JsonArray(favourites.filterNot { it.hasUrl(url) })).withVersion()
    }

    /** Deletes a sticker from My stickers and from the favourites. */
    fun delete(content: JsonObject, shortcode: String): JsonObject {
        val images = content.obj("images") ?: return content.withVersion()
        val url = (images[shortcode] as? JsonObject)?.string("url")
        val withoutImage = content.with("images", JsonObject(images - shortcode))
        return if (url != null) unfavourite(withoutImage, url) else withoutImage.withVersion()
    }

    fun shortcode(now: Long, existing: Set<String>): String {
        val base = "s" + now.toString(36)
        if (base !in existing) return base
        return generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it !in existing }
    }

    private fun JsonObject.validImages(): List<OwnSticker> = obj("images").orEmpty().mapNotNull { (shortcode, element) ->
        StickerImage.fromJson(element)?.let { image ->
            OwnSticker(shortcode = shortcode, image = image, addedAt = (element as JsonObject).long(ADDED_KEY))
        }
    }

    private fun JsonElement.hasUrl(url: String): Boolean = (this as? JsonObject)?.string("url") == url
}
