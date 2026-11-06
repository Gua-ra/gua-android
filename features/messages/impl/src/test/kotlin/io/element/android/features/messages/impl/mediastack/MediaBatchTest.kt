/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class MediaBatchTest {
    @Test
    fun `every shared marker case parses as expected`() {
        assertThat(MediaBatchFixtures.markers).isNotEmpty()
        MediaBatchFixtures.markers.forEach { case ->
            assertWithMessage(case.name).that(MediaBatch.fromContent(case.content)).isEqualTo(case.expected)
        }
    }

    @Test
    fun `the extra content of a batch item parses back to the same batch`() {
        val batch = MediaBatch(id = "5b0f3c8e-1d2a-4c6b-9f3e-2a7d8c1b4e60", index = 2, count = 4)
        val content = Json.parseToJsonElement(
            """{"msgtype":"m.image","body":"a.jpg",${batch.extraContentJson().removePrefix("{").removeSuffix("}")}}"""
        ).jsonObject
        assertThat(MediaBatch.fromContent(content)).isEqualTo(batch)
    }

    @Test
    fun `a new batch numbers its items in order under one id`() {
        val batch = MediaBatch.newBatch(3)
        assertThat(batch.map { it.index }).containsExactly(0, 1, 2).inOrder()
        assertThat(batch.map { it.count }.toSet()).containsExactly(3)
        assertThat(batch.map { it.id }.toSet()).hasSize(1)
        assertThat(batch.first().id).matches("[0-9a-f-]{36}")
    }

    @Test
    fun `an edited member is read from its original content`() {
        val original = """{"content":{"msgtype":"m.image","body":"a.jpg","global.gua.batch":{"v":1,"id":"b1","index":1,"count":2}}}"""
        val edit = """{"content":{"msgtype":"m.image","body":"* a.jpg","m.new_content":{"msgtype":"m.image","body":"a.jpg"},""" +
            """"m.relates_to":{"rel_type":"m.replace","event_id":"${'$'}a"}}}"""
        assertThat(MediaBatch.fromEventJson(latestJson = edit, originalJson = { original })).isEqualTo(MediaBatch("b1", 1, 2))
    }

    @Test
    fun `an event without JSON or with invalid JSON has no batch`() {
        assertThat(MediaBatch.fromEventJson(latestJson = null, originalJson = { error("Not read") })).isNull()
        assertThat(MediaBatch.fromEventJson(latestJson = "not json", originalJson = { error("Not read") })).isNull()
    }
}
