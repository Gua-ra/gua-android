/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.forward.impl

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.ForwardEventException
import io.element.android.libraries.matrix.api.timeline.ForwardExtraContents
import io.element.android.libraries.matrix.api.timeline.ForwardProgress
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.AN_EVENT_ID_3
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.A_ROOM_ID_3
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.LiveTimelineProvider
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class ForwardMessagesPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state`() = runTest {
        val presenter = createForwardMessagesPresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.forwardAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - forward successful`() = runTest {
        val forwardEventsLambda = lambdaRecorder { _: List<EventId>, _: List<RoomId>, _: ForwardProgress ->
            Result.success(Unit)
        }
        val timeline = FakeTimeline().apply {
            this.forwardEventsLambda = { eventIds, roomIds, progress -> forwardEventsLambda(eventIds, roomIds, progress) }
        }
        val room = FakeJoinedRoom(liveTimeline = timeline)
        val presenter = createForwardMessagesPresenter(fakeRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val summary = aRoomSummary()
            presenter.onRoomSelected(listOf(summary.roomId))
            val forwardingState = awaitItem()
            assertThat(forwardingState.forwardAction.isLoading()).isTrue()
            val successfulForwardState = awaitItem()
            assertThat(successfulForwardState.forwardAction).isEqualTo(AsyncAction.Success(listOf(summary.roomId)))
            forwardEventsLambda.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - forwarding a media stack sends each event with its extra content`() = runTest {
        val forwarder = RecordingForwarder()
        val extraContents = ForwardExtraContents { _, _ -> listOf("""{"index":0}""", """{"index":1}""") }
        val presenter = createForwardMessagesPresenter(
            eventIds = listOf(AN_EVENT_ID, AN_EVENT_ID_2),
            fakeRoom = forwarder.aRoom(),
            extraContents = extraContents,
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(listOf(A_ROOM_ID))
            skipItems(1)
            assertThat(awaitItem().forwardAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID)))
            assertThat(forwarder.sends).containsExactly(
                RecordingForwarder.Send(A_ROOM_ID, AN_EVENT_ID, """{"index":0}"""),
                RecordingForwarder.Send(A_ROOM_ID, AN_EVENT_ID_2, """{"index":1}"""),
            ).inOrder()
        }
    }

    @Test
    fun `present - select a room and forward failed, then clear`() = runTest {
        val forwardEventsLambda = lambdaRecorder { _: List<EventId>, _: List<RoomId>, _: ForwardProgress ->
            Result.failure<Unit>(IllegalStateException("error"))
        }
        val timeline = FakeTimeline().apply {
            this.forwardEventsLambda = { eventIds, roomIds, progress -> forwardEventsLambda(eventIds, roomIds, progress) }
        }
        val room = FakeJoinedRoom(liveTimeline = timeline)
        val presenter = createForwardMessagesPresenter(fakeRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            val summary = aRoomSummary()
            presenter.onRoomSelected(listOf(summary.roomId))
            skipItems(1)
            val failedForwardState = awaitItem()
            assertThat(failedForwardState.forwardAction.isFailure()).isTrue()
            // Then clear error
            failedForwardState.eventSink(ForwardMessagesEvent.ClearError)
            assertThat(awaitItem().forwardAction.isUninitialized()).isTrue()
            forwardEventsLambda.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - a forward retried after it failed part way sends the room only the events it has not received`() = runTest {
        val forwarder = RecordingForwarder(failingSends = setOf(A_ROOM_ID to 1))
        val presenter = createForwardMessagesPresenter(
            eventIds = listOf(AN_EVENT_ID, AN_EVENT_ID_2, AN_EVENT_ID_3),
            fakeRoom = forwarder.aRoom(),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(listOf(A_ROOM_ID))
            skipItems(1)
            val failedState = awaitItem()
            assertThat(failedState.forwardAction.isFailure()).isTrue()
            failedState.eventSink(ForwardMessagesEvent.ClearError)
            assertThat(awaitItem().forwardAction.isUninitialized()).isTrue()

            presenter.onRoomSelected(listOf(A_ROOM_ID))
            skipItems(1)
            assertThat(awaitItem().forwardAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID)))
            assertThat(forwarder.sends).containsExactly(
                RecordingForwarder.Send(A_ROOM_ID, AN_EVENT_ID),
                RecordingForwarder.Send(A_ROOM_ID, AN_EVENT_ID_2),
                RecordingForwarder.Send(A_ROOM_ID, AN_EVENT_ID_3),
            ).inOrder()
        }
    }

    @Test
    fun `present - forwarding again sends nothing to a room that received everything the first time`() = runTest {
        val forwarder = RecordingForwarder(failingSends = setOf(A_ROOM_ID_2 to 0))
        val presenter = createForwardMessagesPresenter(
            eventIds = listOf(AN_EVENT_ID, AN_EVENT_ID_2),
            fakeRoom = forwarder.aRoom(),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(listOf(A_ROOM_ID, A_ROOM_ID_2))
            skipItems(1)
            assertThat(awaitItem().forwardAction.isFailure()).isTrue()

            presenter.onRoomSelected(listOf(A_ROOM_ID, A_ROOM_ID_2))
            skipItems(1)
            assertThat(awaitItem().forwardAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID, A_ROOM_ID_2)))
            assertThat(forwarder.sends).containsExactly(
                RecordingForwarder.Send(A_ROOM_ID, AN_EVENT_ID),
                RecordingForwarder.Send(A_ROOM_ID, AN_EVENT_ID_2),
                RecordingForwarder.Send(A_ROOM_ID_2, AN_EVENT_ID),
                RecordingForwarder.Send(A_ROOM_ID_2, AN_EVENT_ID_2),
            ).inOrder()
        }
    }

    @Test
    fun `present - when another room failed, dismissing the error closes the picker to the rooms that received everything`() = runTest {
        val forwarder = RecordingForwarder(failingSends = setOf(A_ROOM_ID_2 to 0))
        val presenter = createForwardMessagesPresenter(fakeRoom = forwarder.aRoom())
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(listOf(A_ROOM_ID, A_ROOM_ID_2))
            skipItems(1)
            val failedState = awaitItem()
            assertThat(failedState.forwardAction.isFailure()).isTrue()
            failedState.eventSink(ForwardMessagesEvent.ClearError)
            assertThat(awaitItem().forwardAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID)))
        }
    }

    @Test
    fun `present - when the error is not a ForwardEventException, dismissing it still closes the picker to the rooms that received everything`() = runTest {
        val forwarder = RecordingForwarder(
            failingSends = setOf(A_ROOM_ID_2 to 0),
            failure = { IllegalStateException("Room object has already been destroyed") },
        )
        val presenter = createForwardMessagesPresenter(eventIds = listOf(AN_EVENT_ID, AN_EVENT_ID_2), fakeRoom = forwarder.aRoom())
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(listOf(A_ROOM_ID, A_ROOM_ID_2))
            skipItems(1)
            val failedState = awaitItem()
            assertThat(failedState.forwardAction.isFailure()).isTrue()
            failedState.eventSink(ForwardMessagesEvent.ClearError)
            assertThat(awaitItem().forwardAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID)))
        }
    }

    @Test
    fun `present - when a send times out, dismissing the error closes the picker to the rooms that received everything`() = runTest {
        val forwarder = RecordingForwarder(timingOutSends = setOf(A_ROOM_ID_2 to 1))
        val presenter = createForwardMessagesPresenter(eventIds = listOf(AN_EVENT_ID, AN_EVENT_ID_2), fakeRoom = forwarder.aRoom())
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(listOf(A_ROOM_ID, A_ROOM_ID_2))
            skipItems(1)
            val failedState = awaitItem()
            assertThat(failedState.forwardAction.isFailure()).isTrue()
            failedState.eventSink(ForwardMessagesEvent.ClearError)
            assertThat(awaitItem().forwardAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID)))
        }
    }

    @Test
    fun `present - when every room failed, dismissing the error keeps the picker open`() = runTest {
        val forwarder = RecordingForwarder(failingSends = setOf(A_ROOM_ID to 0, A_ROOM_ID_2 to 1))
        val presenter = createForwardMessagesPresenter(eventIds = listOf(AN_EVENT_ID, AN_EVENT_ID_2), fakeRoom = forwarder.aRoom())
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(listOf(A_ROOM_ID, A_ROOM_ID_2))
            skipItems(1)
            val failedState = awaitItem()
            assertThat(failedState.forwardAction.isFailure()).isTrue()
            failedState.eventSink(ForwardMessagesEvent.ClearError)
            assertThat(awaitItem().forwardAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - dismissing the error never closes the picker to a room that could not be found`() = runTest {
        val forwarder = RecordingForwarder(failingSends = setOf(A_ROOM_ID_3 to 0), unresolvedRoomIds = setOf(A_ROOM_ID_2))
        val presenter = createForwardMessagesPresenter(fakeRoom = forwarder.aRoom())
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            presenter.onRoomSelected(listOf(A_ROOM_ID, A_ROOM_ID_2, A_ROOM_ID_3))
            skipItems(1)
            val failedState = awaitItem()
            assertThat(failedState.forwardAction.isFailure()).isTrue()
            failedState.eventSink(ForwardMessagesEvent.ClearError)
            assertThat(awaitItem().forwardAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID)))
        }
    }
}

// Forwards one room after another through the progress, as RoomContentForwarder does, and records each send; each send in failingSends fails once.
private class RecordingForwarder(
    failingSends: Set<Pair<RoomId, Int>> = emptySet(),
    private val timingOutSends: Set<Pair<RoomId, Int>> = emptySet(),
    private val unresolvedRoomIds: Set<RoomId> = emptySet(),
    private val failure: (List<RoomId>) -> Exception = ::ForwardEventException,
) {
    data class Send(val roomId: RoomId, val eventId: EventId, val extraContent: String? = null)

    private val failingSends = failingSends.toMutableSet()
    val sends = mutableListOf<Send>()

    fun aRoom() = FakeJoinedRoom(liveTimeline = FakeTimeline().apply { forwardEventsLambda = this@RecordingForwarder::forward })

    private suspend fun forward(eventIds: List<EventId>, roomIds: List<RoomId>, progress: ForwardProgress): Result<Unit> {
        // Unresolved rooms go unreported, so only the progress can tell the presenter that they got nothing.
        val failedRoomIds = (roomIds - unresolvedRoomIds).filter { roomId ->
            runCatchingExceptions {
                progress.forwardTo(roomId, eventIds) { index, extraContent ->
                    if (roomId to index in timingOutSends) withTimeout(1.milliseconds) { awaitCancellation() }
                    check(!failingSends.remove(roomId to index)) { "Sending failed" }
                    sends += Send(roomId, eventIds[index], extraContent)
                }
            }.isFailure
        }
        return if (failedRoomIds.isEmpty()) Result.success(Unit) else Result.failure(failure(failedRoomIds))
    }
}

fun TestScope.createForwardMessagesPresenter(
    eventIds: List<EventId> = listOf(AN_EVENT_ID),
    fakeRoom: FakeJoinedRoom = FakeJoinedRoom(),
    extraContents: ForwardExtraContents? = null,
) = ForwardMessagesPresenter(
    eventIds = eventIds,
    timelineProvider = LiveTimelineProvider(fakeRoom),
    extraContents = extraContents,
    sessionCoroutineScope = this,
)
