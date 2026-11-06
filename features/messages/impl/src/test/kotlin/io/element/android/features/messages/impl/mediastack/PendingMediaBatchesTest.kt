/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.TransactionId
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class PendingMediaBatchesTest {
    private val photo = PendingMediaBatches.Media(filename = "IMG_0412.jpg", fileSize = 812_331, mimeType = "image/jpeg")
    private val video = PendingMediaBatches.Media(filename = "IMG_0413.mp4", fileSize = 4_096, mimeType = "video/mp4")
    private val clock = FakeSystemClock()
    private val pendingMediaBatches = PendingMediaBatches(clock)

    @Test
    fun `a local echo is matched by its file, then by its transaction id`() {
        val batch = MediaBatch.makeBatches(2).requireNoNulls()
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to batch[0], video to batch[1]))

        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t2"), video)).isEqualTo(batch[1])
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo)).isEqualTo(batch[0])
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), video)).isEqualTo(batch[0])
    }

    @Test
    fun `the same photo sent in two batches keeps each echo in its own batch`() {
        val first = MediaBatch.makeBatches(2).requireNoNulls()
        val second = MediaBatch.makeBatches(2).requireNoNulls()
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to first[0], video to first[1]))
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to second[0], video to second[1]))

        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo)).isEqualTo(first[0])
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t3"), photo)).isEqualTo(second[0])
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo)).isEqualTo(first[0])
    }

    @Test
    fun `an echo in another chat or with another file is not matched`() {
        val batch = MediaBatch.makeBatches(2).requireNoNulls()
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to batch[0], video to batch[1]))

        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID_2, TransactionId("t1"), photo)).isNull()
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo.copy(fileSize = 1))).isNull()
    }

    @Test
    fun `an entry is dropped once its event is sent`() {
        val batch = MediaBatch.makeBatches(2).requireNoNulls()
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to batch[0], video to batch[1]))
        pendingMediaBatches.onSent(A_ROOM_ID, batch[0])

        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo)).isNull()
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t2"), video)).isEqualTo(batch[1])
    }

    @Test
    fun `an entry expires after a day`() {
        val batch = MediaBatch.makeBatches(2).requireNoNulls()
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to batch[0], video to batch[1]))
        clock.epochMillisResult += 24.hours.inWholeMilliseconds + 1

        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo)).isNull()
    }

    @Test
    fun `forwarding gives each chat batches of its own, registered for that chat`() {
        val files = List(MediaBatch.MAX_SEND_COUNT) { photo } + video
        val extraContents = pendingMediaBatches.forwardExtraContents(files)

        val first = extraContents.forChat(A_ROOM_ID).map { it?.toBatch() }
        val second = extraContents.forChat(A_ROOM_ID_2).map { it?.toBatch() }

        assertThat(first.dropLast(1).map { it?.count }.toSet()).containsExactly(MediaBatch.MAX_SEND_COUNT)
        assertThat(first.last()).isNull()
        assertThat(second.first()?.id).isNotEqualTo(first.first()?.id)
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo)).isEqualTo(first.first())
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID_2, TransactionId("t2"), photo)).isEqualTo(second.first())
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t3"), video)).isNull()
    }

    @Test
    fun `a forward that stops part way drops the batches of the members it did not send, so a retry stacks as one`() {
        val other = photo.copy(filename = "IMG_0414.jpg")
        val files = listOf(photo, video, other)
        val failed = pendingMediaBatches.forwardExtraContents(files)
        val failedBatches = failed.forChat(A_ROOM_ID).map { it?.toBatch() }
        val otherChatBatches = failed.forChat(A_ROOM_ID_2).map { it?.toBatch() }
        failed.onUnsent(A_ROOM_ID, fromIndex = 1)

        val retryBatches = pendingMediaBatches.forwardExtraContents(files).forChat(A_ROOM_ID).map { it?.toBatch() }

        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo)).isEqualTo(failedBatches[0])
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t2"), photo)).isEqualTo(retryBatches[0])
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t3"), video)).isEqualTo(retryBatches[1])
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t4"), other)).isEqualTo(retryBatches[2])
        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID_2, TransactionId("t5"), video)).isEqualTo(otherChatBatches[1])
    }

    private fun String.toBatch(): MediaBatch? =
        MediaBatch.fromContent(JsonObject(Json.parseToJsonElement(this).jsonObject + ("msgtype" to JsonPrimitive("m.image"))))
}
