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
        val backgrounds = listOf(ChatBackground.None, aPhoto()) +
            ChatBackgroundPreset.entries.map(ChatBackground::Preset) +
            ChatArtwork.entries.map(ChatBackground::Art)
        val bubbles = listOf(null) + ChatBubbleColor.entries
        backgrounds.forEach { background ->
            bubbles.forEach { bubble ->
                bubbles.forEach { bubbleDark ->
                    val theme = ChatTheme(background, bubble, bubbleDark).normalized()
                    assertThat(ChatBackgroundCodec.decode(ChatBackgroundCodec.encode(theme))).isEqualTo(theme)
                }
            }
        }
    }

    @Test
    fun `an artwork is written with its fallback preset, and only a dark bubble that differs is written`() {
        assertThat(ChatBackgroundCodec.encode(ChatArtwork.GlassDrops.theme))
            .isEqualTo("""{"art":"glass-drops","bubble":"purple","bubble_dark":"pink","fallback_preset":"blush","kind":"art","v":1}""")
        assertThat(ChatBackgroundCodec.encode(ChatArtwork.Silk.theme))
            .isEqualTo("""{"art":"silk","bubble":"teal","fallback_preset":"mint","kind":"art","v":1}""")
        assertThat(ChatBackgroundCodec.encode(ChatTheme(bubbleDark = ChatBubbleColor.Pink))).isEqualTo("{}")
    }

    @Test
    fun `the dark bubble shows only in dark appearance and only next to a palette bubble`() {
        val pair = ChatTheme(bubble = ChatBubbleColor.Purple, bubbleDark = ChatBubbleColor.Pink)
        assertThat(pair.outgoingBubble(isLight = true)).isEqualTo(ChatBubbleColor.Purple)
        assertThat(pair.outgoingBubble(isLight = false)).isEqualTo(ChatBubbleColor.Pink)
        assertThat(ChatTheme(bubble = ChatBubbleColor.Teal).outgoingBubble(isLight = false)).isEqualTo(ChatBubbleColor.Teal)
        assertThat(ChatTheme(bubbleDark = ChatBubbleColor.Pink).outgoingBubble(isLight = false)).isNull()
        assertThat(ChatTheme(bubbleDark = ChatBubbleColor.Pink).normalized()).isEqualTo(ChatTheme.STOCK)
        assertThat(ChatTheme(bubble = ChatBubbleColor.Teal, bubbleDark = ChatBubbleColor.Teal).normalized()).isEqualTo(ChatTheme(bubble = ChatBubbleColor.Teal))
    }

    @Test
    fun `a theme with no wallpaper is named after the bubble shown in each appearance`() {
        val pair = ChatTheme(bubble = ChatBubbleColor.Orange, bubbleDark = ChatBubbleColor.Blue)
        assertThat(pair.nameRes(isLight = true)).isEqualTo(ChatBubbleColor.Orange.nameRes)
        assertThat(pair.nameRes(isLight = false)).isEqualTo(ChatBubbleColor.Blue.nameRes)
        val palmCoast = ChatArtwork.PalmCoast.theme
        assertThat(palmCoast.nameRes(isLight = false)).isEqualTo(ChatArtwork.PalmCoast.nameRes)
        assertThat(ChatTheme.STOCK.nameRes(isLight = false)).isEqualTo(ChatBackground.None.nameRes)
    }

    @Test
    fun `artwork ids are unique, valid and never a reserved word`() {
        val ids = ChatArtwork.entries.map { it.id }

        assertThat(ids).hasSize(49)
        assertThat(ids).containsNoDuplicates()
        assertThat(ids).containsNoneOf("rainbow", "pattern", "video", "Blue")
        ids.forEach { assertThat(ChatArtwork.fromId(it)?.id).isEqualTo(it) }
        assertThat(ChatArtwork.fromId("Silk")).isNull()
    }

    @Test
    fun `an artwork's fallback is one of the original ten presets`() {
        val originalTen = ChatBackgroundPreset.entries.take(10)
        ChatArtwork.entries.forEach { assertWithMessage(it.id).that(it.fallbackPreset).isIn(originalTen) }
    }

    @Test
    fun `exactly the Photos section holds the photos, each with a credit and one image for both appearances`() {
        ChatArtwork.entries.forEach { artwork ->
            val message = assertWithMessage(artwork.id)
            message.that(artwork.isPhoto).isEqualTo(artwork.section == ChatArtworkSection.Photos)
            message.that(artwork.isPhoto).isEqualTo(artwork.id.startsWith("photo-"))
            if (artwork.isPhoto) {
                message.that(artwork.isLive).isFalse()
                message.that(artwork.lightFile).isEqualTo("${artwork.id}.webp")
                message.that(artwork.darkFile).isEqualTo("${artwork.id}.webp")
            } else {
                message.that(artwork.lightFile).isEqualTo("${artwork.id}-light.webp")
            }
        }
        assertThat(ChatArtwork.photos).isEqualTo(ChatArtwork.entries.filter { it.section == ChatArtworkSection.Photos })
        assertThat(ChatBackground.Art(ChatArtwork.PhotoIpe).photoCredit).isEqualTo(ChatArtwork.PhotoIpe.credit)
        assertThat(ChatBackground.Art(ChatArtwork.Ipe).photoCredit).isNull()
        assertThat(ChatBackground.Preset(ChatBackgroundPreset.Sky).photoCredit).isNull()
    }

    @Test
    fun `the photo licences carry their own names and deed links`() {
        assertThat(ChatPhotoLicence.entries.map { it.displayName to it.deedUrl }).containsExactly(
            "CC0 1.0" to "https://creativecommons.org/publicdomain/zero/1.0/",
            "CC BY 4.0" to "https://creativecommons.org/licenses/by/4.0/",
        ).inOrder()
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
        is ChatBackground.Art -> {
            put("kind", "art")
            put("art", background.artwork.id)
        }
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
    bubbleDark?.let { put("bubble_dark", it.id) }
}.normalized()

private fun JsonObject.toChatTheme(): ChatTheme {
    val background = when (getValue("kind").jsonPrimitive.content) {
        "none" -> ChatBackground.None
        "preset" -> ChatBackground.Preset(ChatBackgroundPreset.fromId(getValue("preset").jsonPrimitive.content)!!)
        "art" -> ChatBackground.Art(ChatArtwork.fromId(getValue("art").jsonPrimitive.content)!!)
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
    val bubbleDark = get("bubble_dark")?.jsonPrimitive?.content?.let { ChatBubbleColor.fromId(it)!! }
    return ChatTheme(background, bubble, bubbleDark)
}

// Fixtures may write 1 or 1.0 for the same number, so numbers are compared by value.
internal fun JsonElement.normalized(): JsonElement = when (this) {
    is JsonObject -> JsonObject(mapValues { it.value.normalized() })
    is JsonPrimitive -> if (isString) this else doubleOrNull?.let { JsonPrimitive(it) } ?: this
    else -> this
}
