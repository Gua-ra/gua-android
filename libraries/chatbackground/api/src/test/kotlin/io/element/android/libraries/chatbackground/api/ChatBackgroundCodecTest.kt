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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Test

class ChatBackgroundCodecTest {
    @Test
    fun `the shared fixtures decode as expected`() {
        val cases = sharedFixtures().getValue("cases").jsonArray

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val name = case.jsonObject.getValue("name").jsonPrimitive.content
            val decoded = ChatBackgroundCodec.decode(case.jsonObject.getValue("content").toString())
            assertWithMessage(name).that(decoded.toFixture()).isEqualTo(case.jsonObject.getValue("expected").normalized())
        }
    }

    @Test
    fun `the shared fixtures encode as expected`() {
        val cases = sharedFixtures().getValue("encode_cases").jsonArray

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val name = case.jsonObject.getValue("name").jsonPrimitive.content
            val encoded = ChatBackgroundCodec.encode(case.jsonObject.getValue("theme").jsonObject.toChatTheme())
            assertWithMessage(name).that(encoded).isEqualTo(case.jsonObject.getValue("expected").jsonPrimitive.content)
        }
    }

    @Test
    fun `invalid json and a missing event mean no theme`() {
        assertThat(ChatBackgroundCodec.decode(null)).isEqualTo(ChatTheme.STOCK)
        assertThat(ChatBackgroundCodec.decode("")).isEqualTo(ChatTheme.STOCK)
        assertThat(ChatBackgroundCodec.decode("{not json")).isEqualTo(ChatTheme.STOCK)
    }

    @Test
    fun `presets are written in the shared format`() {
        assertThat(ChatBackgroundCodec.encode(ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Aurora))))
            .isEqualTo("""{"kind":"preset","preset":"aurora","v":1}""")
    }

    @Test
    fun `removal is written as empty content`() {
        assertThat(ChatBackgroundCodec.encode(ChatTheme.STOCK)).isEqualTo("{}")
    }

    @Test
    fun `every theme reads back the same as written`() {
        val backgrounds = listOf(ChatBackground.None, aPhoto()) + ChatBackgroundPreset.entries.map(ChatBackground::Preset)
        val bubbles = listOf(null) + ChatBubbleColor.entries
        backgrounds.forEach { background ->
            bubbles.forEach { bubble ->
                val theme = ChatTheme(background, bubble)
                assertThat(ChatBackgroundCodec.decode(ChatBackgroundCodec.encode(theme))).isEqualTo(theme)
            }
        }
    }

    @Test
    fun `a photo reads back the same as written`() {
        val photo = ChatBackground.Photo(
            url = "mxc://gua.global/AbCdEf",
            mimetype = "image/jpeg",
            width = 1536,
            height = 2048,
            size = 412_337,
            blurhash = "LKO2?U%2Tw=w]~RBVZRi};RPxuwH",
            dim = 60,
            alignX = 250,
            alignY = 1000,
        )

        assertThat(ChatBackgroundCodec.decode(ChatBackgroundCodec.encode(ChatTheme(photo))).background).isEqualTo(photo)
    }

    @Test
    fun `photos are written in the shared format`() {
        val photo = ChatBackground.Photo(
            url = "mxc://gua.global/AbCdEf",
            mimetype = "image/jpeg",
            width = 1536,
            height = 2048,
            size = 412_337,
            blurhash = "LKO2?U%2Tw=w]~RBVZRi};RPxuwH",
        )

        assertThat(ChatBackgroundCodec.encode(ChatTheme(photo))).isEqualTo(
            """{"kind":"photo","photo":{"align":{"x":500,"y":500},"blurhash":"LKO2?U%2Tw=w]~RBVZRi};RPxuwH","dim":35,"h":2048,""" +
                """"mimetype":"image/jpeg","size":412337,"url":"mxc://gua.global/AbCdEf","w":1536},"v":1}"""
        )
    }

    @Test
    fun `a photo without blurhash is written without the field`() {
        val encoded = ChatBackgroundCodec.encode(
            ChatTheme(ChatBackground.Photo(url = "mxc://a/b", mimetype = "image/jpeg", width = 1, height = 1, size = 1, blurhash = null))
        )

        assertThat(Json.parseToJsonElement(encoded).jsonObject.getValue("photo").jsonObject.keys).doesNotContain("blurhash")
    }

    @Test
    fun `preset ids are unique and valid`() {
        val ids = ChatBackgroundPreset.entries.map { it.id }

        assertThat(ids).containsNoDuplicates()
        ids.forEach { assertThat(ChatBackgroundPreset.fromId(it)?.id).isEqualTo(it) }
    }

    @Test
    fun `the presets and bubble colours are the shared ones, in order`() {
        assertThat(ChatBackgroundPreset.entries.map { it.id }).isEqualTo(
            listOf("aurora", "mint", "sky", "sand", "blush", "lavender", "graphite", "sunset", "ocean", "night", "dawn", "forest", "dusk", "citrus")
        )
        assertThat(ChatBubbleColor.entries.map { it.id }).isEqualTo(listOf("green", "teal", "blue", "purple", "pink", "orange", "yellow", "graphite"))
        assertThat(ChatBubbleColor.fromId("standard")).isNull()
    }

    @Test
    fun `the new presets use the shared colours`() {
        assertThat(ChatBackgroundPreset.Dawn.light).isEqualTo(
            ChatBackgroundFill.LinearGradient(UnitPoint(0.5f, 0f), UnitPoint(0.5f, 1f), listOf(GradientStop(0xFFF6D9BF, 0f), GradientStop(0xFFC9DDF0, 1f)))
        )
        assertThat(ChatBackgroundPreset.Forest.dark).isEqualTo(
            ChatBackgroundFill.LinearGradient(UnitPoint(0f, 0f), UnitPoint(1f, 1f), listOf(GradientStop(0xFF07140A, 0f), GradientStop(0xFF0A1A14, 1f)))
        )
        assertThat(ChatBubbleColor.Blue.color(isLight = true)).isEqualTo(0xFFDCE8FA)
        assertThat(ChatBubbleColor.Blue.color(isLight = false)).isEqualTo(0xFF18304F)
    }
}

private fun sharedFixtures() =
    Json.parseToJsonElement(ChatBackgroundCodecTest::class.java.classLoader!!.getResource("chat-background-fixtures.json")!!.readText()).jsonObject

private fun aPhoto() = ChatBackground.Photo(
    url = "mxc://gua.global/AbCdEf",
    mimetype = "image/jpeg",
    width = 1536,
    height = 2048,
    size = 412_337,
    blurhash = null,
)

internal fun ChatTheme.toFixture(): JsonElement = buildJsonObject {
    when (val background = background) {
        ChatBackground.None -> put("kind", "none")
        is ChatBackground.Preset -> {
            put("kind", "preset")
            put("preset", background.preset.id)
        }
        is ChatBackground.Photo -> {
            put("kind", "photo")
            putJsonObject("photo") {
                put("url", background.url)
                put("mimetype", background.mimetype)
                put("w", background.width)
                put("h", background.height)
                put("size", background.size)
                put("blurhash", background.blurhash?.let { JsonPrimitive(it) } ?: JsonNull)
                put("dim", background.dim)
                putJsonObject("align") {
                    put("x", background.alignX)
                    put("y", background.alignY)
                }
            }
        }
    }
    bubble?.let { put("bubble", it.id) }
}.normalized()

private fun JsonObject.toChatTheme(): ChatTheme {
    val background = when (getValue("kind").jsonPrimitive.content) {
        "none" -> ChatBackground.None
        "preset" -> ChatBackground.Preset(ChatBackgroundPreset.fromId(getValue("preset").jsonPrimitive.content)!!)
        else -> getValue("photo").jsonObject.let { photo ->
            val align = photo.getValue("align").jsonObject
            ChatBackground.Photo(
                url = photo.getValue("url").jsonPrimitive.content,
                mimetype = photo.getValue("mimetype").jsonPrimitive.content,
                width = photo.getValue("w").jsonPrimitive.int,
                height = photo.getValue("h").jsonPrimitive.int,
                size = photo.getValue("size").jsonPrimitive.long,
                blurhash = (photo["blurhash"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                dim = photo.getValue("dim").jsonPrimitive.int,
                alignX = align.getValue("x").jsonPrimitive.int,
                alignY = align.getValue("y").jsonPrimitive.int,
            )
        }
    }
    val bubble = get("bubble")?.jsonPrimitive?.content?.takeUnless { it == "standard" }?.let { ChatBubbleColor.fromId(it)!! }
    return ChatTheme(background, bubble)
}

// Fixtures may write 1 or 1.0 for the same number, so numbers are compared by value.
internal fun JsonElement.normalized(): JsonElement = when (this) {
    is JsonObject -> JsonObject(mapValues { it.value.normalized() })
    is JsonPrimitive -> if (isString) this else doubleOrNull?.let { JsonPrimitive(it) } ?: this
    else -> this
}
