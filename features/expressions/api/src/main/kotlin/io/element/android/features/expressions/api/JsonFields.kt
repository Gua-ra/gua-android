/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api

import io.element.android.libraries.core.data.tryOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal val EMPTY_JSON_OBJECT = JsonObject(emptyMap())

/** Parses account data or state content; anything that is not a JSON object reads as `{}`. */
fun parseJsonObject(content: String?): JsonObject {
    if (content.isNullOrBlank()) return EMPTY_JSON_OBJECT
    return tryOrNull { Json.parseToJsonElement(content) } as? JsonObject ?: EMPTY_JSON_OBJECT
}

internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

internal fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

internal fun JsonObject.boolean(key: String): Boolean? = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

internal fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

internal fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray

internal fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))

internal fun JsonElement.sortedKeys(): JsonElement = when (this) {
    is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { it.key to it.value.sortedKeys() })
    is JsonArray -> JsonArray(map { it.sortedKeys() })
    else -> this
}

/** Account data versions below 1 or of the wrong type are written back as 1. */
internal fun JsonObject.withVersion(): JsonObject = with("v", JsonPrimitive(int("v")?.coerceAtLeast(1) ?: 1))
