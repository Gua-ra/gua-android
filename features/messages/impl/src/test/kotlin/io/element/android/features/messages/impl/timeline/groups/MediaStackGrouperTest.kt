/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.groups

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.features.messages.impl.mediastack.MediaBatch
import io.element.android.features.messages.impl.mediastack.MediaBatchFixtures
import io.element.android.features.messages.impl.mediastack.PendingMediaBatches
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemThreadInfo
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRedactedContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemEncryptedContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemDaySeparatorModel
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemReadMarkerModel
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.TransactionId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.ThreadSummary
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import org.junit.Test

class MediaStackGrouperTest {
    private val pendingMediaBatches = PendingMediaBatches(FakeSystemClock())
    private val grouper = TimelineItemGrouper()
    private val mediaStackGrouper = MediaStackGrouper(A_ROOM_ID, pendingMediaBatches)

    @Test
    fun `every shared grouping case renders as expected`() {
        assertThat(MediaBatchFixtures.grouping).isNotEmpty()
        MediaBatchFixtures.grouping.forEach { case ->
            assertWithMessage(case.name).that(groupFixtureItems(case.items)).isEqualTo(case.expected.map { it.normalised() })
        }
    }

    @Test
    fun `a removed later member heading a thread renders on its own`() {
        fun item(id: String, kind: String, index: Int, isThreadRoot: Boolean = false) = MediaBatchFixtures.GroupingItem(
            id = id,
            sender = ALICE.value,
            kind = kind,
            batch = MediaBatch("t", index, 4).takeIf { kind != "redacted" },
            known = MediaBatch("t", index, 4).takeIf { kind == "redacted" },
            isThreadRoot = isThreadRoot,
        )
        val removed = listOf(item("\$r1", "image", 0), item("\$r2", "image", 1), item("\$r3", "redacted", 2, true), item("\$r4", "image", 3))

        assertThat(groupFixtureItems(removed)).containsExactly(
            listOf("stack", "photos", "\$r1", "\$r2"),
            listOf("item", "\$r3"),
            listOf("item", "\$r4"),
        ).inOrder()
    }

    @Test
    fun `the read marker keeps its place beside a removed batch`() {
        val items = listOf(
            MediaBatchFixtures.GroupingItem("\$x1", ALICE.value, "redacted", null, MediaBatch("x", 0, 2), false),
            MediaBatchFixtures.GroupingItem("marker", null, "readMarker", null, null, false),
            MediaBatchFixtures.GroupingItem("\$x2", ALICE.value, "redacted", null, MediaBatch("x", 1, 2), false),
        )

        assertThat(groupFixtureItems(items)).containsExactly(listOf("item", "\$x1"), listOf("readMarker")).inOrder()
    }

    @Test
    fun `a stack keeps its id when older members load in`() {
        val first = photo("\$a1", MediaBatch("b", 0, 3))
        val second = photo("\$a2", MediaBatch("b", 1, 3))
        val third = photo("\$a3", MediaBatch("b", 2, 3))

        val before = group(second, third).single()
        val after = group(first, second, third).single()

        assertThat(after.identifier()).isEqualTo(before.identifier())
        assertThat(after.stackMembers()).containsExactly(EventId("\$a1"), EventId("\$a2"), EventId("\$a3")).inOrder()
    }

    @Test
    fun `two stacks that resolve to the same id get distinct ids, the older one keeping it`() {
        val members = listOf(0, 1, 2, 3).map { index -> photo("\$n$index", MediaBatch("b", index, 4)) }
        val whole = group(*members.toTypedArray()).single()

        val text = aTimelineItemEvent(eventId = EventId("\$text"), content = aTimelineItemTextContent()).copy(id = UniqueId("text"), senderId = ALICE)
        val split = group(members[0], members[1], text, members[2], members[3])

        assertThat(split.map { it.identifier() }.toSet()).hasSize(3)
        assertThat(split.first().identifier()).isEqualTo(whole.identifier())
    }

    @Test
    fun `my unsent photos are stacked from the pending batches`() {
        val batch = MediaBatch.makeBatches(2).requireNoNulls()
        val media = PendingMediaBatches.Media(filename = "A picture.jpg", fileSize = 4 * 1024 * 1024L, mimeType = "image/jpeg")
        pendingMediaBatches.register(A_ROOM_ID, listOf(media to batch[0], media to batch[1]))
        val echoes = listOf("t1", "t2").map { transactionId ->
            aTimelineItemEvent(
                isMine = true,
                content = aTimelineItemImageContent(),
                sendState = LocalEventSendState.Sending.Event,
            ).copy(id = UniqueId(transactionId), eventId = null, transactionId = TransactionId(transactionId), senderId = ALICE)
        }

        val stack = group(*echoes.toTypedArray()).single() as TimelineItem.Event

        val content = stack.content as TimelineItemMediaStackContent
        assertThat(content.members.map { it.event.transactionId }).containsExactly(TransactionId("t1"), TransactionId("t2")).inOrder()
        assertThat(stack.localSendState).isEqualTo(LocalEventSendState.Sending.Event)
    }

    @Test
    fun `a stack is failed when one member failed, and stands for its first member`() {
        val failed = LocalEventSendState.Failed.Unknown("error")
        val first = photo("\$f1", MediaBatch("b", 0, 2))
        val second = photo("\$f2", MediaBatch("b", 1, 2)).copy(localSendState = failed)

        val stack = group(first, second).single() as TimelineItem.Event

        assertThat(stack.eventId).isEqualTo(EventId("\$f1"))
        assertThat(stack.localSendState).isEqualTo(failed)
        assertThat(stack.isEditable).isFalse()
        assertThat((stack.content as TimelineItemMediaStackContent).failedMembers.map { it.eventId }).containsExactly(EventId("\$f2"))
    }

    @Test
    fun `the read receipt of a partly sent stack goes to its newest sent member`() {
        val sent = listOf(0, 1).map { index -> photo("\$s$index", MediaBatch("b", index, 3)) }
        val unsent = photo("\$s2", MediaBatch("b", 2, 3))
            .copy(id = UniqueId("t2"), eventId = null, transactionId = TransactionId("t2"), localSendState = LocalEventSendState.Sending.Event)

        val content = (group(sent[0], sent[1], unsent).single() as TimelineItem.Event).content as TimelineItemMediaStackContent

        assertThat(content.newest.transactionId).isEqualTo(TransactionId("t2"))
        assertThat(content.newestEventId).isEqualTo(EventId("\$s1"))
    }

    @Test
    fun `stacks are not grouped without a media stack grouper`() {
        val items = listOf(photo("\$p1", MediaBatch("b", 0, 2)), photo("\$p2", MediaBatch("b", 1, 2)))
        assertThat(grouper.group(items.reversed())).isEqualTo(items.reversed())
    }

    private fun group(vararg oldestFirst: TimelineItem): List<TimelineItem> =
        grouper.group(oldestFirst.reversed(), mediaStackGrouper).reversed()

    private fun groupFixtureItems(oldestFirst: List<MediaBatchFixtures.GroupingItem>): List<List<String>> {
        val items = oldestFirst.map { it.toTimelineItem() }
        return TimelineItemGrouper().group(items.reversed(), MediaStackGrouper(A_ROOM_ID, pendingMediaBatches)).reversed().map { it.describe() }
    }

    private fun TimelineItem.stackMembers() = ((this as TimelineItem.Event).content as TimelineItemMediaStackContent).members.map { it.event.eventId }

    private fun photo(eventId: String, batch: MediaBatch) = aTimelineItemEvent(eventId = EventId(eventId), content = aTimelineItemImageContent())
        .copy(id = UniqueId(eventId), senderId = ALICE, mediaBatch = batch)

    private fun MediaBatchFixtures.GroupingItem.toTimelineItem(): TimelineItem {
        val content = when (kind) {
            "image" -> aTimelineItemImageContent()
            "video" -> aTimelineItemVideoContent()
            "text" -> aTimelineItemTextContent()
            "redacted" -> TimelineItemRedactedContent
            "utd" -> aTimelineItemEncryptedContent()
            "day" -> return TimelineItem.Virtual(UniqueId(id), TimelineItemDaySeparatorModel("Today"))
            "readMarker" -> return TimelineItem.Virtual(UniqueId(id), TimelineItemReadMarkerModel)
            else -> error("Unknown kind $kind")
        }
        return aTimelineItemEvent(
            eventId = EventId(id),
            content = content,
            threadInfo = if (isThreadRoot) TimelineItemThreadInfo.ThreadRoot(ThreadSummary(AsyncData.Uninitialized, 1), null) else null,
        ).copy(id = UniqueId(id), senderId = UserId(sender!!), mediaBatch = batch ?: known)
    }

    private fun TimelineItem.describe(): List<String> = when (this) {
        is TimelineItem.Virtual -> listOf(if (model is TimelineItemReadMarkerModel) "readMarker" else "day")
        is TimelineItem.GroupedEvents -> listOf("group") + events.map { it.eventId!!.value }
        is TimelineItem.Event -> when (val content = content) {
            is TimelineItemMediaStackContent -> listOf(
                "stack",
                when (content.kind) {
                    TimelineItemMediaStackContent.Kind.Photos -> "photos"
                    TimelineItemMediaStackContent.Kind.Videos -> "videos"
                    TimelineItemMediaStackContent.Kind.Items -> "items"
                },
            ) + content.members.map { it.event.eventId!!.value }
            else -> listOf("item", eventId!!.value)
        }
    }

    // A removed run is one tile, the first removed message, the others being hidden.
    private fun List<String>.normalised(): List<String> = if (first() == "removed") listOf("item", this[1]) else this

    private companion object {
        val ALICE = UserId("@alice:example.org")
    }
}
