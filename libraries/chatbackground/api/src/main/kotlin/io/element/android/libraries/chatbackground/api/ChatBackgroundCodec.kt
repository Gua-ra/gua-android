/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import io.element.android.libraries.core.data.tryOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToLong

/**
 * Reads and writes the content of the [io.element.android.libraries.matrix.api.room.CHAT_BACKGROUND_EVENT_TYPE] state event.
 * iOS implements the same rules, checked by the shared `chat-background-fixtures.json`.
 */
object ChatBackgroundCodec {
    const val REMOVED_CONTENT = "{}"

    private const val VERSION = 1L
    private const val KIND_PRESET = "preset"
    private const val KIND_PHOTO = "photo"
    private const val MAX_DIMENSION = 8192L
    private const val MAX_SIZE = 20_000_000L
    private const val MAX_BLURHASH_LENGTH = 100

    fun decode(contentJson: String?): ChatBackground {
        val content = tryOrNull { Json.parseToJsonElement(contentJson.orEmpty()) } as? JsonObject ?: return ChatBackground.None
        val kind = content["kind"].stringOrNull()
        return when {
            content["v"].integerOrNull() == VERSION && kind == KIND_PRESET -> decodePreset(content["preset"])
            content["v"].integerOrNull() == VERSION && kind == KIND_PHOTO -> decodePhoto(content["photo"])
            else -> decodePreset(content["fallback_preset"])
        }
    }

    fun encode(background: ChatBackground): String = when (background) {
        ChatBackground.None -> REMOVED_CONTENT
        is ChatBackground.Preset -> buildJsonObject {
            put("v", VERSION)
            put("kind", KIND_PRESET)
            put("preset", background.preset.id)
        }.toString()
        is ChatBackground.Photo -> buildJsonObject {
            put("v", VERSION)
            put("kind", KIND_PHOTO)
            putJsonObject("photo") {
                put("url", background.url)
                put("mimetype", background.mimetype)
                put("w", background.width)
                put("h", background.height)
                put("size", background.size)
                background.blurhash?.let { put("blurhash", it) }
                put("dim", background.dim.roundTo(100))
                putJsonObject("align") {
                    put("x", background.alignX.roundTo(1000))
                    put("y", background.alignY.roundTo(1000))
                }
            }
        }.toString()
    }

    private fun decodePreset(element: JsonElement?): ChatBackground {
        return ChatBackgroundPreset.fromId(element.stringOrNull())?.let(ChatBackground::Preset) ?: ChatBackground.None
    }

    private fun decodePhoto(element: JsonElement?): ChatBackground {
        val photo = element as? JsonObject ?: return ChatBackground.None
        val url = photo["url"].stringOrNull()?.takeIf { it.startsWith("mxc://") } ?: return ChatBackground.None
        val mimetype = photo["mimetype"].stringOrNull()?.takeIf { it.startsWith("image/") } ?: return ChatBackground.None
        val width = photo["w"].integerOrNull()?.takeIf { it in 1..MAX_DIMENSION } ?: return ChatBackground.None
        val height = photo["h"].integerOrNull()?.takeIf { it in 1..MAX_DIMENSION } ?: return ChatBackground.None
        val size = photo["size"].integerOrNull()?.takeIf { it in 1..MAX_SIZE } ?: return ChatBackground.None
        val blurhash = photo["blurhash"].optional { it.stringOrNull()?.takeIf { hash -> hash.length <= MAX_BLURHASH_LENGTH } }
        val dim = photo["dim"].optional { it.numberOrNull()?.coerceIn(0.0, ChatBackground.Photo.MAX_DIM) }
        val align = photo["align"].optional { it as? JsonObject }
        val alignX = align.value?.get("x").optional { it.numberOrNull()?.coerceIn(0.0, 1.0) }
        val alignY = align.value?.get("y").optional { it.numberOrNull()?.coerceIn(0.0, 1.0) }
        if (listOf(blurhash, dim, align, alignX, alignY).any { !it.isValid }) return ChatBackground.None
        return ChatBackground.Photo(
            url = url,
            mimetype = mimetype,
            width = width.toInt(),
            height = height.toInt(),
            size = size,
            blurhash = blurhash.value,
            dim = dim.value ?: ChatBackground.Photo.DEFAULT_DIM,
            alignX = alignX.value ?: ChatBackground.Photo.DEFAULT_ALIGN,
            alignY = alignY.value ?: ChatBackground.Photo.DEFAULT_ALIGN,
        )
    }
}

/** An optional field: absent or null is valid with no value, anything present must parse. */
private data class OptionalField<T>(val value: T?, val isValid: Boolean)

private fun <T> JsonElement?.optional(parse: (JsonElement) -> T?): OptionalField<T> {
    if (this == null || this is JsonNull) return OptionalField(null, isValid = true)
    val value = parse(this)
    return OptionalField(value, isValid = value != null)
}

private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement?.numberOrNull(): Double? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull

private fun JsonElement?.integerOrNull(): Long? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

private fun Double.roundTo(scale: Int): Double = (this * scale).roundToLong().toDouble() / scale
