/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.gif

import io.element.android.features.expressions.api.array
import io.element.android.features.expressions.api.string
import io.element.android.features.expressions.api.with
import io.element.android.features.expressions.api.withVersion
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class GifFavourite(
    val id: String,
    val provider: String,
)

data class GifLibrary(
    val favourites: List<GifFavourite>,
) {
    fun isFavourite(id: String, provider: String = GifLibraryCodec.KLIPY): Boolean = GifFavourite(id, provider) in favourites

    companion object {
        val EMPTY = GifLibrary(favourites = emptyList())
    }
}

/** Reads and edits `global.gua.gifs`, keeping keys this version does not know. */
object GifLibraryCodec {
    const val EVENT_TYPE = "global.gua.gifs"
    const val KLIPY = "klipy"
    const val MAX_FAVOURITES = 50

    fun decode(content: JsonObject): GifLibrary = GifLibrary(
        favourites = content.array("favourites").orEmpty().mapNotNull { it.toFavourite() },
    )

    fun favourite(content: JsonObject, id: String, provider: String = KLIPY, limit: Int = MAX_FAVOURITES): JsonObject {
        val favourite = GifFavourite(id, provider)
        val others = content.array("favourites").orEmpty().filterNot { it.toFavourite() == favourite }
        val entry = buildJsonObject {
            put("id", id)
            put("p", provider)
        }
        return content.with("favourites", JsonArray((listOf(entry) + others).take(limit))).withVersion()
    }

    fun unfavourite(content: JsonObject, id: String, provider: String = KLIPY): JsonObject {
        val favourites = content.array("favourites") ?: return content.withVersion()
        val favourite = GifFavourite(id, provider)
        return content.with("favourites", JsonArray(favourites.filterNot { it.toFavourite() == favourite })).withVersion()
    }

    private fun JsonElement.toFavourite(): GifFavourite? {
        val json = this as? JsonObject ?: return null
        return GifFavourite(id = json.string("id") ?: return null, provider = json.string("p") ?: return null)
    }
}
