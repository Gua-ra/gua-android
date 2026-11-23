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
import io.element.android.features.expressions.api.gif.GifFavourite
import io.element.android.features.expressions.api.gif.GifLibraryCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class GifLibraryCodecTest {
    @Test
    fun `the shared fixtures edit as expected`() {
        val cases = SharedFixtures.cases("gifLibrary")

        assertThat(cases).isNotEmpty()
        cases.forEach { case ->
            val start = case.getValue("start").jsonObject
            val op = case.getValue("op").jsonObject
            val id = op.getValue("id").jsonPrimitive.content
            val limit = case["limit"]?.jsonPrimitive?.int ?: GifLibraryCodec.MAX_FAVOURITES

            val result = when (op.getValue("type").jsonPrimitive.content) {
                "favourite" -> GifLibraryCodec.favourite(start, id, limit = limit)
                "unfavourite" -> GifLibraryCodec.unfavourite(start, id)
                else -> error("Unknown operation $op")
            }

            assertWithMessage(case.name).that(result).isEqualTo(case.getValue("expected"))
        }
    }

    @Test
    fun `decoding skips entries without an id or provider`() {
        val content = parseJsonObject("""{"favourites":[{"id":"1","p":"klipy"},{"id":2,"p":"klipy"},{"id":"3"},"4"],"v":1}""")

        assertThat(GifLibraryCodec.decode(content).favourites).containsExactly(GifFavourite(id = "1", provider = "klipy"))
    }

    @Test
    fun `invalid content reads as an empty library`() {
        assertThat(GifLibraryCodec.decode(parseJsonObject("[1]")).favourites).isEmpty()
        assertThat(GifLibraryCodec.decode(JsonObject(emptyMap())).favourites).isEmpty()
    }
}
