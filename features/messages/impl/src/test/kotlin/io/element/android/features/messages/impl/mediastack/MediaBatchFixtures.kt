/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The cases of `media-batch-v1-fixtures.json`, shared byte for byte with Gua iOS.
 */
object MediaBatchFixtures {
    data class MarkerCase(val name: String, val content: JsonObject, val expected: MediaBatch?)

    data class GroupingItem(
        val id: String,
        val sender: String?,
        val kind: String,
        val batch: MediaBatch?,
        val known: MediaBatch?,
        val isThreadRoot: Boolean,
    )

    data class GroupingCase(val name: String, val items: List<GroupingItem>, val expected: List<List<String>>)

    private val root: JsonObject by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("media-batch-v1-fixtures.json").bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject
    }

    val markers: List<MarkerCase> by lazy {
        root.getValue("markers").jsonArray.map { element ->
            val case = element.jsonObject
            MarkerCase(
                name = case.getValue("name").jsonPrimitive.content,
                content = case.getValue("content").jsonObject,
                expected = (case["batch"] as? JsonObject)?.let { batch ->
                    MediaBatch(
                        id = batch.getValue("id").jsonPrimitive.content,
                        index = batch.getValue("index").jsonPrimitive.int,
                        count = batch.getValue("count").jsonPrimitive.int,
                    )
                },
            )
        }
    }

    val grouping: List<GroupingCase> by lazy {
        root.getValue("grouping").jsonArray.map { element ->
            val case = element.jsonObject
            GroupingCase(
                name = case.getValue("name").jsonPrimitive.content,
                items = case.getValue("items").jsonArray.map { item ->
                    val fields = item.jsonObject
                    GroupingItem(
                        id = fields.getValue("id").jsonPrimitive.content,
                        sender = (fields["sender"] as? JsonPrimitive)?.content,
                        kind = fields.getValue("kind").jsonPrimitive.content,
                        batch = (fields["batch"] as? JsonArray)?.toMediaBatch(),
                        known = (fields["known"] as? JsonArray)?.toMediaBatch(),
                        isThreadRoot = (fields["threadRoot"] as? JsonPrimitive)?.content == "true",
                    )
                },
                expected = case.getValue("expected").jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.content } },
            )
        }
    }

    private fun JsonArray.toMediaBatch() = MediaBatch(
        id = this[0].jsonPrimitive.content,
        index = this[1].jsonPrimitive.int,
        count = this[2].jsonPrimitive.int,
    )
}
