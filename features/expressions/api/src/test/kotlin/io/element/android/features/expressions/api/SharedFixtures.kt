/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object SharedFixtures {
    private const val GENERATED_BASE_TIME = 1_762_905_600_000L

    val root: JsonObject by lazy {
        val text = SharedFixtures::class.java.classLoader!!.getResource("stickers-fixtures.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject
    }

    fun cases(vararg path: String): List<JsonObject> {
        val element = path.fold<String, JsonElement>(root) { node, key -> node.jsonObject.getValue(key) }
        return element.jsonArray.map { it.jsonObject }
    }

    val JsonObject.name: String get() = getValue("name").jsonPrimitive.content

    /** Expands the `generate` shorthand the fixture notes describe. */
    fun expandStart(start: JsonObject): JsonObject {
        val generate = start["generate"] as? JsonObject ?: return start
        val imageCount = generate.getValue("images").jsonPrimitive.int
        val favouriteCount = generate.getValue("favourites").jsonPrimitive.int
        return buildJsonObject {
            put("favourites", buildJsonArray {
                repeat(favouriteCount) { i -> add(image(url = "mxc://gua.global/f$i", body = "F$i")) }
            })
            putJsonObject("images") {
                repeat(imageCount) { i ->
                    val added = GENERATED_BASE_TIME + i
                    put("s" + added.toString(36), JsonObject(image(url = "mxc://gua.global/s$i", body = "S$i") + ("global.gua.added" to JsonPrimitive(added))))
                }
            }
            put("v", 1)
        }
    }

    private val sharedInfo = buildJsonObject {
        put("h", 256)
        put("mimetype", "image/png")
        put("size", 120_034)
        put("w", 256)
    }

    private fun image(url: String, body: String) = buildJsonObject {
        put("body", body)
        put("info", sharedInfo)
        put("url", url)
    }
}
