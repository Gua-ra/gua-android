/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.forward.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.ForwardEventException
import io.element.android.libraries.matrix.api.timeline.ForwardExtraContents
import io.element.android.libraries.matrix.api.timeline.ForwardProgress
import io.element.android.libraries.matrix.api.timeline.TimelineProvider
import io.element.android.libraries.matrix.api.timeline.getActiveTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@AssistedInject
class ForwardMessagesPresenter(
    @Assisted private val eventIds: List<EventId>,
    @Assisted private val timelineProvider: TimelineProvider,
    @Assisted extraContents: ForwardExtraContents?,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) : Presenter<ForwardMessagesState> {
    @AssistedFactory
    fun interface Factory {
        fun create(eventIds: List<EventId>, timelineProvider: TimelineProvider, extraContents: ForwardExtraContents?): ForwardMessagesPresenter
    }

    private val forwardingActionState: MutableState<AsyncAction<List<RoomId>>> = mutableStateOf(AsyncAction.Uninitialized)

    // GUA FORK: one per forward, so that forwarding again after a failure sends each room only what it has not received.
    private val forwardProgress = ForwardProgress(extraContents)

    // GUA FORK: the rooms that received everything when another failed; as on iOS, the picker then closes once the error is dismissed.
    @Volatile
    private var forwardedRoomIds = emptyList<RoomId>()

    fun onRoomSelected(roomIds: List<RoomId>) {
        sessionCoroutineScope.forwardEvents(eventIds, roomIds)
    }

    @Composable
    override fun present(): ForwardMessagesState {
        fun handleEvent(event: ForwardMessagesEvent) {
            when (event) {
                ForwardMessagesEvent.ClearError -> forwardingActionState.value = if (forwardedRoomIds.isEmpty()) {
                    AsyncAction.Uninitialized
                } else {
                    AsyncAction.Success(forwardedRoomIds)
                }
            }
        }

        return ForwardMessagesState(
            forwardAction = forwardingActionState.value,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.forwardEvents(
        eventIds: List<EventId>,
        roomIds: List<RoomId>,
    ) = launch {
        suspend {
            timelineProvider.getActiveTimeline().forwardEvents(eventIds, roomIds, forwardProgress)
                .onFailure {
                    Timber.e(it, "Error while forwarding events")
                    forwardedRoomIds = (it as? ForwardEventException)?.let { error -> roomIds - error.roomIds.toSet() }.orEmpty()
                }
                .getOrThrow()
            roomIds
        }.runCatchingUpdatingState(forwardingActionState)
    }
}
