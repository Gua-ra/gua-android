/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.features.expressions.api.gif.GifRenditions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

class GifRenditionFixturesTest {
    @Test
    fun `the shared fixtures choose the rendition and body as expected`() {
        val text = GifRenditionFixturesTest::class.java.classLoader!!.getResource("stickers-fixtures.json")!!.readText()
        val cases = Json.parseToJsonElement(text).jsonObject.getValue("gifRendition").jsonArray.map { it.jsonObject }

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val item = KlipyJson.item(case.getValue("item").jsonObject)

            val selection = item?.let(GifRenditions::forSending)

            val actual: JsonElement = selection?.let {
                buildJsonObject {
                    put("body", it.body)
                    put("height", it.file.height)
                    put("rendition", it.size.key)
                    put("size", it.file.size)
                    put("thumbnailUrl", it.still?.url?.let(::JsonPrimitive) ?: JsonNull)
                    put("url", it.file.url)
                    put("width", it.file.width)
                }
            } ?: JsonNull
            assertWithMessage(case.getValue("name").jsonPrimitive.content).that(actual).isEqualTo(case.getValue("expected"))
        }
    }
}
