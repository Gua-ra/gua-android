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
import io.element.android.features.expressions.api.sticker.StickerPack
import io.element.android.features.expressions.api.sticker.StickerPackCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

class StickerPackCodecTest {
    @Test
    fun `the shared fixtures read as expected`() {
        val cases = SharedFixtures.cases("packs")

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val pack = StickerPackCodec.decode(
                eventType = case.getValue("eventType").jsonPrimitive.content,
                stateKey = case.getValue("stateKey").jsonPrimitive.content,
                content = case.getValue("content").jsonObject,
                roomName = case.getValue("roomName").jsonPrimitive.content,
                roomAvatarUrl = null,
            )

            assertWithMessage(case.name).that(pack.toFixture()).isEqualTo(case.getValue("expected"))
        }
    }

    @Test
    fun `a pack without an avatar falls back to the room avatar`() {
        val pack = StickerPackCodec.decode(
            eventType = StickerPackCodec.EVENT_TYPE,
            stateKey = "",
            content = parseJsonObject("""{"pack":{"display_name":" "}}"""),
            roomName = "Family",
            roomAvatarUrl = "mxc://gua.global/room",
        )

        assertThat(pack.displayName).isEqualTo("Family")
        assertThat(pack.avatarUrl).isEqualTo("mxc://gua.global/room")
    }

    @Test
    fun `image usage only counts in the unstable format`() {
        val content = parseJsonObject("""{"images":{"a":{"url":"mxc://gua.global/a","usage":["emoticon"]}},"pack":{"usage":["sticker"]}}""")

        val pack = StickerPackCodec.decode(StickerPackCodec.EVENT_TYPE, "", content, roomName = "Family", roomAvatarUrl = null)

        assertThat(pack.stickers.map { it.shortcode }).containsExactly("a")
    }

    private fun StickerPack.toFixture(): JsonElement = buildJsonObject {
        put("avatarUrl", avatarUrl?.let(::JsonPrimitive) ?: JsonNull)
        put("displayName", displayName)
        put("stickers", JsonArray(stickers.map { sticker ->
            buildJsonObject {
                put("body", sticker.image.body?.let(::JsonPrimitive) ?: JsonNull)
                put("info", sticker.image.info ?: JsonNull)
                put("shortcode", sticker.shortcode)
                put("url", sticker.image.url)
            }
        }))
    }
}
