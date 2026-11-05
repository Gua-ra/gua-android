/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Test

class ChatBackgroundCodecTest {
    @Test
    fun `the shared fixtures decode as expected`() {
        val fixtures = javaClass.classLoader!!.getResource("chat-background-fixtures.json")!!.readText()
        val cases = Json.parseToJsonElement(fixtures).jsonObject.getValue("cases").jsonArray

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val name = case.jsonObject.getValue("name").jsonPrimitive.content
            val decoded = ChatBackgroundCodec.decode(case.jsonObject.getValue("content").toString())
            assertWithMessage(name).that(decoded.toFixture()).isEqualTo(case.jsonObject.getValue("expected").normalized())
        }
    }

    @Test
    fun `invalid json and a missing event mean no background`() {
        assertThat(ChatBackgroundCodec.decode(null)).isEqualTo(ChatBackground.None)
        assertThat(ChatBackgroundCodec.decode("")).isEqualTo(ChatBackground.None)
        assertThat(ChatBackgroundCodec.decode("{not json")).isEqualTo(ChatBackground.None)
    }

    @Test
    fun `presets are written with the version and kind`() {
        assertThat(ChatBackgroundCodec.encode(ChatBackground.Preset(ChatBackgroundPreset.Aurora)))
            .isEqualTo("""{"v":1,"kind":"preset","preset":"aurora"}""")
    }

    @Test
    fun `removal is written as empty content`() {
        assertThat(ChatBackgroundCodec.encode(ChatBackground.None)).isEqualTo("{}")
    }

    @Test
    fun `photos are written with rounded dim and align, and read back the same`() {
        val photo = ChatBackground.Photo(
            url = "mxc://gua.global/AbCdEf",
            mimetype = "image/jpeg",
            width = 1536,
            height = 2048,
            size = 412_337,
            blurhash = "LKO2?U%2Tw=w]~RBVZRi};RPxuwH",
            dim = 0.05 * 7,
            alignX = 0.25,
            alignY = 0.5,
        )

        val encoded = ChatBackgroundCodec.encode(photo)

        assertThat(encoded).isEqualTo(
            """{"v":1,"kind":"photo","photo":{"url":"mxc://gua.global/AbCdEf","mimetype":"image/jpeg","w":1536,"h":2048,""" +
                """"size":412337,"blurhash":"LKO2?U%2Tw=w]~RBVZRi};RPxuwH","dim":0.35,"align":{"x":0.25,"y":0.5}}}"""
        )
        assertThat(ChatBackgroundCodec.decode(encoded)).isEqualTo(photo.copy(dim = 0.35))
    }

    @Test
    fun `a photo without blurhash is written without the field`() {
        val encoded = ChatBackgroundCodec.encode(
            ChatBackground.Photo(url = "mxc://a/b", mimetype = "image/jpeg", width = 1, height = 1, size = 1, blurhash = null)
        )

        assertThat(Json.parseToJsonElement(encoded).jsonObject.getValue("photo").jsonObject.keys).doesNotContain("blurhash")
    }

    @Test
    fun `preset ids are unique and valid`() {
        val ids = ChatBackgroundPreset.entries.map { it.id }

        assertThat(ids).containsNoDuplicates()
        ids.forEach { assertThat(ChatBackgroundPreset.fromId(it)?.id).isEqualTo(it) }
    }
}

private fun ChatBackground.toFixture(): JsonElement = when (this) {
    ChatBackground.None -> buildJsonObject { put("kind", "none") }
    is ChatBackground.Preset -> buildJsonObject {
        put("kind", "preset")
        put("preset", preset.id)
    }
    is ChatBackground.Photo -> buildJsonObject {
        put("kind", "photo")
        putJsonObject("photo") {
            put("url", url)
            put("mimetype", mimetype)
            put("w", width)
            put("h", height)
            put("size", size)
            put("blurhash", blurhash?.let { JsonPrimitive(it) } ?: JsonNull)
            put("dim", dim)
            putJsonObject("align") {
                put("x", alignX)
                put("y", alignY)
            }
        }
    }
}.normalized()

// Fixtures may write 1 or 1.0 for the same number, so numbers are compared by value.
private fun JsonElement.normalized(): JsonElement = when (this) {
    is JsonObject -> JsonObject(mapValues { it.value.normalized() })
    is JsonPrimitive -> if (isString) this else doubleOrNull?.let { JsonPrimitive(it) } ?: this
    else -> this
}
