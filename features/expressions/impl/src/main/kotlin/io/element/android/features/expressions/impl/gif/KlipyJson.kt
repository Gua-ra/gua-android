/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import io.element.android.features.expressions.api.gif.GifFile
import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifPage
import io.element.android.features.expressions.api.gif.GifRendition
import io.element.android.features.expressions.api.gif.GifSize
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal object KlipyJson {
    private const val GIF_TYPE = "gif"

    fun page(root: JsonObject, requestedPage: Int): GifPage {
        val data = root["data"] as? JsonObject
        val items = (data?.get("data") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::item) }
        return GifPage(
            items = items,
            page = data?.primitive("current_page")?.intOrNull ?: requestedPage,
            hasNext = data?.primitive("has_next")?.booleanOrNull ?: false,
        )
    }

    /** Null for anything but a GIF; KLIPY mixes other types, ads included, into some results. */
    fun item(json: JsonObject): GifItem? {
        if (json.primitive("type")?.content != GIF_TYPE) return null
        // Favourites and recents store the slug: KLIPY looks items up by slug, not by numeric id.
        val slug = json.primitive("slug")?.content?.takeIf { it.isNotBlank() } ?: return null
        val files = json["file"] as? JsonObject
        val renditions = GifSize.entries.mapNotNull { size ->
            val formats = files?.get(size.key) as? JsonObject ?: return@mapNotNull null
            val rendition = GifRendition(animated = file(formats["webp"]), still = file(formats["jpg"]))
            (size to rendition).takeIf { rendition.animated != null }
        }.toMap()
        return GifItem(
            id = slug,
            title = json.primitive("title")?.takeIf { it.isString }?.content.orEmpty(),
            renditions = renditions,
        )
    }

    private fun file(element: JsonElement?): GifFile? {
        val json = element as? JsonObject ?: return null
        val url = json.primitive("url")?.takeIf { it.isString }?.content ?: return null
        return GifFile(
            url = url,
            width = json.primitive("width")?.intOrNull ?: return null,
            height = json.primitive("height")?.intOrNull ?: return null,
            size = json.primitive("size")?.longOrNull ?: return null,
        )
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = get(key) as? JsonPrimitive
}
