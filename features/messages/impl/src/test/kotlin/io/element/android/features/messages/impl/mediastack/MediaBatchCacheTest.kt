/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.TransactionId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.timeline.item.event.ImageMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.LatestJsonProvider
import io.element.android.libraries.matrix.api.timeline.item.event.TimelineItemDebugInfoProvider
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.timeline.aMessageContent
import io.element.android.libraries.matrix.test.timeline.aRedactedContent
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import org.junit.Test

class MediaBatchCacheTest {
    private val pendingMediaBatches = PendingMediaBatches(FakeSystemClock())
    private val cache = MediaBatchCache(FakeBaseRoom(roomId = A_ROOM_ID), pendingMediaBatches)

    @Test
    fun `a photo is parsed once`() {
        var reads = 0
        val photo = aPhoto(
            latestJson = {
                reads++
                markedJson("b1", 0)
            },
        )

        assertThat(cache.batchOf(photo)).isEqualTo(MediaBatch("b1", 0, 2))
        assertThat(cache.batchOf(photo)).isEqualTo(MediaBatch("b1", 0, 2))
        assertThat(reads).isEqualTo(1)
    }

    @Test
    fun `a removed photo is still known by its batch`() {
        cache.batchOf(aPhoto(latestJson = { markedJson("b1", 1) }))

        assertThat(cache.batchOf(anEventTimelineItem(eventId = AN_EVENT_ID, content = aRedactedContent()))).isEqualTo(MediaBatch("b1", 1, 2))
    }

    @Test
    fun `an edited photo is read from its original content`() {
        val edit = """{"content":{"msgtype":"m.image","body":"* a.jpg","m.relates_to":{"rel_type":"m.replace","event_id":"${'$'}a"}}}"""
        val photo = aPhoto(
            latestJson = { edit },
            debugInfo = { aTimelineItemDebugInfo(originalJson = markedJson("b1", 0)) },
        )

        assertThat(cache.batchOf(photo)).isEqualTo(MediaBatch("b1", 0, 2))
    }

    @Test
    fun `a message that is not a photo or a video has no batch`() {
        val text = anEventTimelineItem(content = aMessageContent(), latestJsonProvider = { error("Not read") })

        assertThat(cache.batchOf(text)).isNull()
    }

    @Test
    fun `the server copy of my photo drops its pending batch`() {
        val media = PendingMediaBatches.Media(filename = "a.jpg", fileSize = 1, mimeType = "image/jpeg")
        pendingMediaBatches.register(A_ROOM_ID, listOf(media to MediaBatch("b1", 0, 2)))

        cache.batchOf(aPhoto(latestJson = { markedJson("b1", 0) }, isOwn = true))

        assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), media)).isNull()
    }

    private fun aPhoto(
        latestJson: LatestJsonProvider,
        debugInfo: TimelineItemDebugInfoProvider = TimelineItemDebugInfoProvider { aTimelineItemDebugInfo() },
        isOwn: Boolean = false,
    ) = anEventTimelineItem(
        eventId = AN_EVENT_ID,
        isOwn = isOwn,
        content = aMessageContent(messageType = ImageMessageType("a.jpg", null, null, MediaSource("url"), null)),
        debugInfoProvider = debugInfo,
        latestJsonProvider = latestJson,
    )

    private fun markedJson(id: String, index: Int) =
        """{"content":{"msgtype":"m.image","body":"a.jpg","global.gua.batch":{"v":1,"id":"$id","index":$index,"count":2}}}"""
}
