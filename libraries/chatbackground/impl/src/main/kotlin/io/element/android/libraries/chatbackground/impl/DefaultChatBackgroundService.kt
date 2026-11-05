/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundCodec
import io.element.android.libraries.chatbackground.api.ChatBackgroundService
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CHAT_BACKGROUND_EVENT_TYPE
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.services.appnavstate.api.AppForegroundStateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultChatBackgroundService(
    private val roomStateClient: RoomStateClient,
    private val cache: ChatBackgroundCache,
    private val appForegroundStateService: AppForegroundStateService,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : ChatBackgroundService {
    private data class Slot(
        val stored: StoredChatBackground?,
        /** Counts changes from this device, the timeline and the SDK, so a server reply in flight meanwhile can tell it is stale. */
        val localRevision: Long,
        /** The event ID of a change sent from this device that neither the server nor the timeline has confirmed yet. */
        val pendingEventId: String? = null,
    )

    private val mutex = Mutex()
    private val slots = ConcurrentHashMap<RoomId, MutableStateFlow<Slot>>()

    @Volatile
    private var loadedCache: Map<RoomId, StoredChatBackground>? = null
    private val cached = sessionCoroutineScope.async(start = CoroutineStart.LAZY) { cache.load().also { loadedCache = it } }

    override fun preload() {
        cached.start()
    }

    override fun currentBackground(roomId: RoomId): ChatBackground {
        val stored = slots[roomId]?.value?.stored ?: loadedCache?.get(roomId)
        return ChatBackgroundCodec.decode(stored?.contentJson)
    }

    override fun backgroundFlow(room: JoinedRoom): Flow<ChatBackground> = channelFlow {
        val slot = slot(room.roomId)
        launch {
            appForegroundStateService.isInForeground.filter { it }.collect { refresh(room.roomId) }
        }
        launch {
            room.stateEventsFlow(StateEventType.Custom(CHAT_BACKGROUND_EVENT_TYPE)).collect { events ->
                val event = events.firstOrNull { it.stateKey.isEmpty() } ?: return@collect
                val eventId = event.eventId ?: return@collect
                val timestamp = event.timestamp ?: return@collect
                offer(room.roomId, StoredChatBackground(event.contentJson, eventId.value, timestamp))
            }
        }
        slot.map { ChatBackgroundCodec.decode(it.stored?.contentJson) }
            .distinctUntilChanged()
            .collect { send(it) }
    }

    override fun onTimelineEvent(roomId: RoomId, eventId: EventId, timestamp: Long, contentJson: String?) {
        contentJson ?: return
        sessionCoroutineScope.launch {
            offer(roomId, StoredChatBackground(contentJson, eventId.value, timestamp))
        }
    }

    override suspend fun set(room: JoinedRoom, background: ChatBackground): Result<Unit> {
        val content = ChatBackgroundCodec.encode(background)
        return room.sendStateEventRaw(CHAT_BACKGROUND_EVENT_TYPE, "", content)
            .onFailure { Timber.e(it, "Failed to send the chat background of ${room.roomId}") }
            .map { eventId ->
                update(room.roomId) { current ->
                    // Never the device clock: a clock running ahead would make the next real change look older.
                    val timestamp = (current.stored?.timestamp ?: 0) + 1
                    Slot(StoredChatBackground(content, eventId.value, timestamp), current.localRevision + 1, pendingEventId = eventId.value)
                }
            }
    }

    private suspend fun refresh(roomId: RoomId) {
        val revisionAtStart = slot(roomId).value.localRevision
        val stored = when (val result = roomStateClient.chatBackground(roomId)) {
            is RoomStateResult.Found -> result.stored
            RoomStateResult.NotFound -> StoredChatBackground(ChatBackgroundCodec.REMOVED_CONTENT, eventId = null, timestamp = 0)
            is RoomStateResult.Failed -> {
                Timber.w(result.cause, "Failed to read the chat background of $roomId")
                return
            }
        }
        // The server's state wins whatever its timestamp, and using a chat keeps it in the cache.
        update(roomId, saveUnchanged = true) { current ->
            if (current.localRevision != revisionAtStart && current.outlives(stored)) current else current.copy(stored = stored, pendingEventId = null)
        }
    }

    /** Whether a change seen while [reply] was in flight outlives it: an unconfirmed send from this device, or a newer change. */
    private fun Slot.outlives(reply: StoredChatBackground): Boolean {
        val local = stored ?: return false
        if (local.eventId == reply.eventId) return false
        return pendingEventId != null && local.eventId == pendingEventId || local.timestamp > reply.timestamp
    }

    private suspend fun offer(roomId: RoomId, candidate: StoredChatBackground) {
        update(roomId) { current ->
            val stored = current.stored
            val isNewer = stored == null || candidate.eventId == stored.eventId || candidate.timestamp > stored.timestamp
            if (isNewer && candidate != stored) Slot(candidate, current.localRevision + 1) else current
        }
    }

    private suspend fun update(roomId: RoomId, saveUnchanged: Boolean = false, transform: (Slot) -> Slot) {
        val slot = slot(roomId)
        mutex.withLock {
            val current = slot.value
            val updated = transform(current)
            slot.value = updated
            val stored = updated.stored ?: return
            if (saveUnchanged || stored != current.stored) {
                cache.save(roomId, stored)
            }
        }
    }

    private suspend fun slot(roomId: RoomId): MutableStateFlow<Slot> {
        val cachedBackgrounds = cached.await()
        return mutex.withLock {
            slots.getOrPut(roomId) { MutableStateFlow(Slot(cachedBackgrounds[roomId], localRevision = 0)) }
        }
    }
}
