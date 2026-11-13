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
import io.element.android.features.expressions.api.sticker.StickerContent
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerRelation
import io.element.android.features.expressions.api.sticker.StickerSize
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test

class StickerContentTest {
    @Test
    fun `the shared fixtures encode as expected, keys sorted`() {
        val cases = SharedFixtures.cases("stickerContent")

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val input = case.getValue("input") as JsonObject
            val image = StickerContent.image(
                url = input.getValue("url").jsonPrimitive.content,
                body = input.getValue("body").jsonPrimitive.content,
                pixelWidth = input.getValue("width").jsonPrimitive.int,
                pixelHeight = input.getValue("height").jsonPrimitive.int,
                mimetype = input.getValue("mimetype").jsonPrimitive.content,
                size = input.getValue("size").jsonPrimitive.long,
                isAnimated = input["isAnimated"]?.jsonPrimitive?.boolean == true,
            )
            val replyTo = input["replyTo"]?.jsonPrimitive?.content?.let(::EventId)
            val threadRoot = input["threadRoot"]?.jsonPrimitive?.content?.let(::ThreadId)
            val relation = when {
                threadRoot != null && replyTo != null -> StickerRelation.Thread(rootId = threadRoot, latestEventId = replyTo)
                replyTo != null -> StickerRelation.Reply(replyTo)
                else -> StickerRelation.None
            }

            val encoded = StickerContent.encode(image, relation)

            assertWithMessage(case.name).that(encoded).isEqualTo(case.getValue("expected").sortedKeys().toString())
        }
    }

    @Test
    fun `an image without a body is sent as Sticker`() {
        val encoded = StickerContent.encode(StickerImage(url = "mxc://gua.global/cat", body = null, info = null), StickerRelation.None)

        assertThat(encoded).isEqualTo("""{"body":"Sticker","info":{},"url":"mxc://gua.global/cat"}""")
    }

    @Test
    fun `a very thin image keeps a display size of at least one`() {
        assertThat(StickerContent.displaySize(pixelWidth = 2000, pixelHeight = 1)).isEqualTo(StickerSize(width = 256, height = 1))
    }
}
