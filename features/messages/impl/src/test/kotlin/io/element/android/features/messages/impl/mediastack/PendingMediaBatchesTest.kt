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
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class PendingMediaBatchesTest {
    private val photo = PendingMediaBatches.Media(filename = "IMG_0412.jpg", fileSize = 812_331, mimeType = "image/jpeg")
    private val video = PendingMediaBatches.Media(filename = "IMG_0413.mp4", fileSize = 4_096, mimeType = "video/mp4")
    private val clock = FakeSystemClock()
    private val pendingMediaBatches = PendingMediaBatches(clock)

    @Test
    fun `a local echo is matched by its file, then by its transaction id`() {
        val batch = MediaBatch.newBatch(2)
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to batch[0], video to batch[1]))

        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t2"), video)).isEqualTo(batch[1])
        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t1"), photo)).isEqualTo(batch[0])
        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t1"), video)).isEqualTo(batch[0])
    }

    @Test
    fun `the same photo sent in two batches keeps each echo in its own batch`() {
        val first = MediaBatch.newBatch(2)
        val second = MediaBatch.newBatch(2)
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to first[0], video to first[1]))
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to second[0], video to second[1]))

        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t1"), photo)).isEqualTo(first[0])
        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t3"), photo)).isEqualTo(second[0])
        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t1"), photo)).isEqualTo(first[0])
    }

    @Test
    fun `an echo in another chat or with another file is not matched`() {
        val batch = MediaBatch.newBatch(2)
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to batch[0], video to batch[1]))

        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID_2, TransactionId("t1"), photo)).isNull()
        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t1"), photo.copy(fileSize = 1))).isNull()
    }

    @Test
    fun `an entry is dropped once its event is sent`() {
        val batch = MediaBatch.newBatch(2)
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to batch[0], video to batch[1]))
        pendingMediaBatches.onSent(A_ROOM_ID, batch[0])

        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t1"), photo)).isNull()
        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t2"), video)).isEqualTo(batch[1])
    }

    @Test
    fun `an entry expires after a day`() {
        val batch = MediaBatch.newBatch(2)
        pendingMediaBatches.register(A_ROOM_ID, listOf(photo to batch[0], video to batch[1]))
        clock.epochMillisResult += 24.hours.inWholeMilliseconds + 1

        assertThat(pendingMediaBatches.batchFor(A_ROOM_ID, TransactionId("t1"), photo)).isNull()
    }
}
