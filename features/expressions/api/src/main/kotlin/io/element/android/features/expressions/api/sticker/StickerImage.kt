/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import io.element.android.features.expressions.api.boolean
import io.element.android.features.expressions.api.int
import io.element.android.features.expressions.api.long
import io.element.android.features.expressions.api.obj
import io.element.android.features.expressions.api.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * An image pack image object. [info] is kept as received, so writing it back loses nothing.
 */
data class StickerImage(
    val url: String,
    val body: String?,
    val info: JsonObject?,
) {
    val width: Int? get() = info?.int("w")
    val height: Int? get() = info?.int("h")
    val mimetype: String? get() = info?.string("mimetype")
    val size: Long? get() = info?.long("size")
    val isAnimated: Boolean get() = info?.boolean("is_animated") == true || mimetype == "image/gif"

    fun toJson(): JsonObject = buildJsonObject {
        body?.let { put("body", JsonPrimitive(it)) }
        info?.let { put("info", it) }
        put("url", JsonPrimitive(url))
    }

    companion object {
        /** Null for anything that is not an object with an mxc `url`. */
        fun fromJson(element: JsonElement, bodyFallback: String? = null): StickerImage? {
            val json = element as? JsonObject ?: return null
            val url = json.string("url")?.takeIf { it.startsWith("mxc://") } ?: return null
            return StickerImage(
                url = url,
                body = json.string("body") ?: bodyFallback,
                info = json.obj("info"),
            )
        }
    }
}
