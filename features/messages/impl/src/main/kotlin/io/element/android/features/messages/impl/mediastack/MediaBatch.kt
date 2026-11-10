/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

data class MediaBatch(
    val id: String,
    val index: Int,
    val count: Int,
) {
    fun extraContentJson(): String = buildJsonObject {
        putJsonObject(CONTENT_KEY) {
            put("v", VERSION)
            put("id", id)
            put("index", index)
            put("count", count)
        }
    }.toString()

    companion object {
        const val MAX_SEND_COUNT = 20
        private const val CONTENT_KEY = "global.gua.batch"
        private const val VERSION = 1
        private const val MAX_RECEIVED_COUNT = 100
        private val MEDIA_MSG_TYPES = setOf("m.image", "m.video")
        private val ID_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")
        private val INTEGER_LITERAL = Regex("^-?(0|[1-9][0-9]*)$")

        // Every send and every forward is split this way: batches of up to MAX_SEND_COUNT in order, each under its own id, a lone last item with none.
        fun makeBatches(count: Int): List<MediaBatch?> = (0 until count).chunked(MAX_SEND_COUNT).flatMap { chunk ->
            if (chunk.size < 2) List(chunk.size) { null } else newBatch(chunk.size)
        }

        private fun newBatch(count: Int): List<MediaBatch> {
            val id = UUID.randomUUID().toString()
            return List(count) { index -> MediaBatch(id = id, index = index, count = count) }
        }

        // The marker belongs to the original content, so an edit is read from the original JSON.
        fun fromEventJson(latestJson: String?, originalJson: () -> String?): MediaBatch? {
            val content = latestJson?.eventContent() ?: return null
            return if (content.isReplacement()) {
                originalJson()?.eventContent()?.let(::fromContent)
            } else {
                fromContent(content)
            }
        }

        fun fromContent(content: JsonObject): MediaBatch? {
            if (content.string("msgtype") !in MEDIA_MSG_TYPES || content.isReplacement()) return null
            val marker = content[CONTENT_KEY] as? JsonObject ?: return null
            if (marker.integer("v") != VERSION) return null
            val id = marker.string("id")?.takeIf(ID_PATTERN::matches) ?: return null
            val index = marker.integer("index") ?: return null
            val count = marker.integer("count") ?: return null
            if (count !in 2..MAX_RECEIVED_COUNT || index !in 0 until count) return null
            return MediaBatch(id = id, index = index, count = count)
        }

        private fun String.eventContent(): JsonObject? = runCatchingExceptions { Json.parseToJsonElement(this) }
            .getOrNull()
            .let { it as? JsonObject }
            ?.get("content") as? JsonObject

        private fun JsonObject.isReplacement(): Boolean = (this["m.relates_to"] as? JsonObject)?.string("rel_type") == "m.replace"

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        // Booleans and float literals such as 1.0 are rejected, so only a literal written as an integer counts.
        private fun JsonObject.integer(key: String): Int? = (this[key] as? JsonPrimitive)
            ?.takeIf { !it.isString && INTEGER_LITERAL.matches(it.content) }
            ?.content
            ?.toIntOrNull()
    }
}
