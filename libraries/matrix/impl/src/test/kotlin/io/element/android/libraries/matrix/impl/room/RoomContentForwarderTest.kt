/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.ForwardEventException
import io.element.android.libraries.matrix.api.timeline.ForwardProgress
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustEventTimelineItem
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustTimelineItemContentMsgLike
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.AN_EVENT_ID_3
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.NoHandle
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomListException
import org.matrix.rustcomponents.sdk.RoomListService
import org.matrix.rustcomponents.sdk.RoomMessageEventContentWithoutRelation
import org.matrix.rustcomponents.sdk.SendHandle
import org.matrix.rustcomponents.sdk.Timeline

class RoomContentForwarderTest {
    private val eventIds = listOf(AN_EVENT_ID, AN_EVENT_ID_2, AN_EVENT_ID_3)
    private val sends = mutableListOf<Pair<RoomId, EventId>>()

    @Test
    fun `a room whose send fails is the only one reported, the others get every event, and a retry resumes where it stopped`() = runTest {
        val failingSends = mutableSetOf(A_ROOM_ID_2 to AN_EVENT_ID_2)
        val forwarder = createRoomContentForwarder(setOf(A_ROOM_ID, A_ROOM_ID_2), failingSends)
        val progress = ForwardProgress(eventIds)

        val error = runCatchingExceptions { forwarder.forward(SourceTimeline(), eventIds, listOf(A_ROOM_ID, A_ROOM_ID_2), progress) }.exceptionOrNull()

        assertThat(error).isInstanceOf(ForwardEventException::class.java)
        assertThat((error as ForwardEventException).roomIds).containsExactly(A_ROOM_ID_2)
        assertThat(sentTo(A_ROOM_ID)).containsExactlyElementsIn(eventIds).inOrder()
        assertThat(sentTo(A_ROOM_ID_2)).containsExactly(AN_EVENT_ID)
        assertThat(progress.hasSentAll(A_ROOM_ID)).isTrue()
        assertThat(progress.hasSentAll(A_ROOM_ID_2)).isFalse()

        forwarder.forward(SourceTimeline(), eventIds, listOf(A_ROOM_ID, A_ROOM_ID_2), progress)

        assertThat(sentTo(A_ROOM_ID)).containsExactlyElementsIn(eventIds).inOrder()
        assertThat(sentTo(A_ROOM_ID_2)).containsExactlyElementsIn(eventIds).inOrder()
        assertThat(progress.hasSentAll(A_ROOM_ID_2)).isTrue()
    }

    @Test
    fun `a chosen room that cannot be found is reported as failed`() = runTest {
        val forwarder = createRoomContentForwarder(existingRoomIds = setOf(A_ROOM_ID))
        val progress = ForwardProgress(eventIds)

        val error = runCatchingExceptions { forwarder.forward(SourceTimeline(), eventIds, listOf(A_ROOM_ID, A_ROOM_ID_2), progress) }.exceptionOrNull()

        assertThat(error).isInstanceOf(ForwardEventException::class.java)
        assertThat((error as ForwardEventException).roomIds).containsExactly(A_ROOM_ID_2)
        assertThat(sentTo(A_ROOM_ID)).containsExactlyElementsIn(eventIds).inOrder()
        assertThat(progress.hasSentAll(A_ROOM_ID_2)).isFalse()
    }

    private fun sentTo(roomId: RoomId) = sends.filter { it.first == roomId }.map { it.second }

    private fun createRoomContentForwarder(
        existingRoomIds: Set<RoomId>,
        failingSends: MutableSet<Pair<RoomId, EventId>> = mutableSetOf(),
    ) = RoomContentForwarder(
        roomListService = TargetRoomListService { roomId ->
            if (roomId in existingRoomIds) TargetRoom(roomId, TargetTimeline(roomId, sends, failingSends)) else null
        },
        contentWithoutRelation = { message -> ForwardedContent(EventId(message.body)) },
    )
}

private class SourceTimeline : Timeline(NoHandle) {
    override suspend fun getEventTimelineItemByEventId(eventId: String): EventTimelineItem =
        aRustEventTimelineItem(content = aRustTimelineItemContentMsgLike(body = eventId))
}

private class ForwardedContent(val eventId: EventId) : RoomMessageEventContentWithoutRelation(NoHandle)

private class TargetRoomListService(private val roomOf: (RoomId) -> Room?) : RoomListService(NoHandle) {
    override fun room(roomId: String): Room = roomOf(RoomId(roomId)) ?: throw RoomListException.RoomNotFound(roomId)
}

// Like the SDK's Room, which throws from every call once use() has destroyed it.
private class TargetRoom(private val roomId: RoomId, private val timeline: Timeline) : Room(NoHandle) {
    private var isDestroyed = false

    override fun id(): String {
        check(!isDestroyed) { "Room object has already been destroyed" }
        return roomId.value
    }

    override suspend fun timeline(): Timeline {
        check(!isDestroyed) { "Room object has already been destroyed" }
        return timeline
    }

    override fun destroy() {
        isDestroyed = true
    }
}

// Each send in failingSends fails once.
private class TargetTimeline(
    private val roomId: RoomId,
    private val sends: MutableList<Pair<RoomId, EventId>>,
    private val failingSends: MutableSet<Pair<RoomId, EventId>>,
) : Timeline(NoHandle) {
    override suspend fun send(msg: RoomMessageEventContentWithoutRelation): SendHandle = record(msg)

    override suspend fun sendWithExtraContent(msg: RoomMessageEventContentWithoutRelation, extraContentJson: String?): SendHandle = record(msg)

    private suspend fun record(msg: RoomMessageEventContentWithoutRelation): SendHandle {
        // Lets the other rooms' sends run in between, as they do on a device.
        yield()
        val eventId = (msg as ForwardedContent).eventId
        check(!failingSends.remove(roomId to eventId)) { "Sending failed" }
        sends += roomId to eventId
        return SendHandle(NoHandle)
    }
}
