/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.features.expressions.api.SharedFixtures.name
import io.element.android.features.expressions.api.sticker.OwnSticker
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerLibrary
import io.element.android.features.expressions.api.sticker.StickerLibraryCodec
import io.element.android.features.expressions.api.sticker.StickerLimitReachedException
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Test

class StickerLibraryCodecTest {
    @Test
    fun `the shared fixtures decode as expected`() {
        val cases = SharedFixtures.cases("library", "decode")

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val decoded = StickerLibraryCodec.decode(case.getValue("input").jsonObject)

            assertWithMessage(case.name).that(decoded.toFixture()).isEqualTo(case.getValue("expected"))
        }
    }

    @Test
    fun `the shared fixtures edit as expected`() {
        val cases = SharedFixtures.cases("library", "operations")

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val start = SharedFixtures.expandStart(case.getValue("start").jsonObject)
            val op = case.getValue("op").jsonObject
            val result = runCatchingExceptions { apply(start, op) }

            when {
                case["expectedError"] != null -> {
                    assertWithMessage(case.name).that(case.getValue("expectedError").jsonPrimitive.content).isEqualTo("limitReached")
                    assertWithMessage(case.name).that(result.exceptionOrNull()).isInstanceOf(StickerLimitReachedException::class.java)
                }
                case["expectedFavouritesCount"] != null -> {
                    val favourites = result.getOrThrow().getValue("favourites").jsonArray
                    assertWithMessage(case.name).that(favourites.size).isEqualTo(case.getValue("expectedFavouritesCount").jsonPrimitive.int)
                    assertWithMessage(case.name).that(favourites.first()).isEqualTo(case.getValue("expectedFirstFavourite"))
                    assertWithMessage(case.name).that(favourites.last()).isEqualTo(case.getValue("expectedLastFavourite"))
                }
                else -> assertWithMessage(case.name).that(result.getOrThrow()).isEqualTo(case.getValue("expected"))
            }
        }
    }

    @Test
    fun `the shared shortcode fixtures generate as expected`() {
        val cases = SharedFixtures.root.getValue("shortcodes").jsonArray.map { it.jsonObject }

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val existing = case.getValue("existing").jsonArray.map { it.jsonPrimitive.content }.toSet()

            val shortcode = StickerLibraryCodec.shortcode(case.getValue("now").jsonPrimitive.long, existing)

            assertThat(shortcode).isEqualTo(case.getValue("expected").jsonPrimitive.content)
        }
    }

    @Test
    fun `a library read back after an add shows the new sticker first`() {
        val content = StickerLibraryCodec.add(JsonObject(emptyMap()), aStickerImage("mxc://gua.global/a"), now = 1_000)
        val updated = StickerLibraryCodec.add(content, aStickerImage("mxc://gua.global/b"), now = 2_000)

        assertThat(StickerLibraryCodec.decode(updated).stickers.map { it.image.url })
            .containsExactly("mxc://gua.global/b", "mxc://gua.global/a")
            .inOrder()
    }

    @Test
    fun `a list is written only when it has entries or was already there`() {
        assertThat(StickerLibraryCodec.unfavourite(parseJsonObject("{}"), "mxc://gua.global/a")).isEqualTo(parseJsonObject("""{"v":1}"""))
        assertThat(StickerLibraryCodec.delete(parseJsonObject("""{"v":3}"""), "a")).isEqualTo(parseJsonObject("""{"v":3}"""))
        assertThat(StickerLibraryCodec.unfavourite(parseJsonObject("""{"favourites":[{"url":"mxc://gua.global/a"}]}"""), "mxc://gua.global/a"))
            .isEqualTo(parseJsonObject("""{"favourites":[],"v":1}"""))
    }

    @Test
    fun `invalid images do not count towards the limit`() {
        val images = (0 until StickerLibraryCodec.MAX_STICKERS).associate { "x$it" to JsonPrimitive(it) }
        val content = JsonObject(mapOf("images" to JsonObject(images)))

        val updated = StickerLibraryCodec.add(content, aStickerImage("mxc://gua.global/a"), now = 0)

        assertThat(StickerLibraryCodec.decode(updated).stickers).hasSize(1)
    }

    private fun apply(start: JsonObject, op: JsonObject): JsonObject = when (op.getValue("type").jsonPrimitive.content) {
        "add" -> StickerLibraryCodec.add(
            content = start,
            image = StickerImage.fromJson(op)!!,
            now = op.getValue("now").jsonPrimitive.long,
        )
        "favourite" -> StickerLibraryCodec.favourite(start, StickerImage.fromJson(op.getValue("image"))!!)
        "unfavourite" -> StickerLibraryCodec.unfavourite(start, op.getValue("url").jsonPrimitive.content)
        "delete" -> StickerLibraryCodec.delete(start, op.getValue("shortcode").jsonPrimitive.content)
        else -> error("Unknown operation $op")
    }

    private fun aStickerImage(url: String) = StickerImage(url = url, body = "Cat", info = null)

    private fun StickerLibrary.toFixture(): JsonElement = buildJsonObject {
        put("favourites", JsonArray(favourites.map { it.toJson() }))
        put("images", JsonArray(stickers.map { it.toFixture() }))
    }

    private fun OwnSticker.toFixture(): JsonElement = buildJsonObject {
        put("added", addedAt?.let(::JsonPrimitive) ?: JsonNull)
        put("body", image.body?.let(::JsonPrimitive) ?: JsonNull)
        put("info", image.info ?: JsonNull)
        put("shortcode", shortcode)
        put("url", image.url)
    }
}
