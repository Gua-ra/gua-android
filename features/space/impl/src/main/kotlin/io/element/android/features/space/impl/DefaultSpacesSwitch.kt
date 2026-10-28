/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.space.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.space.api.SpacesSwitch
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

internal const val SPACES_ACCOUNT_DATA_TYPE = "global.gua.spaces"
internal val ENABLE_WHEN_JOINED_TIMEOUT = 60.seconds

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSpacesSwitch(
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : SpacesSwitch {
    private val state = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = state.asStateFlow()

    private val writeMutex = Mutex()
    private val lock = Any()

    // Guarded by lock.
    private var requestCount = 0L
    private var requestedByUser = false
    private var writesInFlight = 0
    private var unechoedWrite: Boolean? = null
    private var restartedSinceWrite = false
    private var lastStored = false
    private var hasStoredValue = false

    init {
        matrixClient.syncService.syncState
            .filter { it == SyncState.Running }
            .onEach { refresh(fromRoomList = false) }
            .launchIn(sessionCoroutineScope)

        // The room list reaches Running only after a sync response is stored, so this read sees changes made on another phone.
        matrixClient.roomListService.state
            .onEach { if (it != RoomListService.State.Running) synchronized(lock) { restartedSinceWrite = true } }
            .filter { it == RoomListService.State.Running }
            .onEach { refresh(fromRoomList = true) }
            .launchIn(sessionCoroutineScope)

        // An account with no stored value that is already in a space starts with Spaces on. Evaluated only once a sync
        // response is stored, so a stored value always wins over this write.
        combine(
            matrixClient.roomListService.state.map { it == RoomListService.State.Running },
            matrixClient.spaceService.topLevelSpacesFlow.map { spaces -> spaces.any { it.state == CurrentUserMembership.JOINED } },
        ) { isRunning, isInSpace -> isRunning && isInSpace }
            .distinctUntilChanged()
            .filter { it }
            .onEach { enableIfUnset() }
            .launchIn(sessionCoroutineScope)
    }

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        val request = synchronized(lock) {
            requestedByUser = true
            request(enabled)
        }
        // The write runs in the session scope so that leaving the screen does not cancel it.
        return sessionCoroutineScope.async { write(enabled, request) }.await()
    }

    override fun enableWhenJoined(roomId: RoomId) {
        sessionCoroutineScope.launch {
            withTimeoutOrNull(ENABLE_WHEN_JOINED_TIMEOUT) {
                matrixClient.getRoomInfoFlow(roomId).first { it.getOrNull()?.currentUserMembership == CurrentUserMembership.JOINED }
            } ?: return@launch
            if (!state.value) setEnabled(true)
        }
    }

    private fun request(enabled: Boolean): Long {
        requestCount++
        writesInFlight++
        state.value = enabled
        return requestCount
    }

    private suspend fun write(enabled: Boolean, request: Long, isMigration: Boolean = false): Result<Unit> = writeMutex.withLock {
        val result = matrixClient.setAccountData(SPACES_ACCOUNT_DATA_TYPE, """{"enabled":$enabled}""")
        synchronized(lock) {
            writesInFlight--
            result
                .onSuccess {
                    unechoedWrite = enabled
                    restartedSinceWrite = false
                    lastStored = enabled
                    hasStoredValue = true
                }
                .onFailure {
                    // A failed migration keeps the decided default on until a value is stored.
                    if (request == requestCount && (hasStoredValue || !isMigration)) {
                        state.value = lastStored
                    }
                }
        }
        result
    }

    private suspend fun enableIfUnset() {
        if (synchronized(lock) { hasStoredValue || requestedByUser || writesInFlight > 0 }) return
        val stored = matrixClient.getAccountData(SPACES_ACCOUNT_DATA_TYPE).getOrElse { return }
        if (stored != null) {
            applyStored(parseEnabled(stored), fromRoomList = true)
            return
        }
        val request = synchronized(lock) {
            if (hasStoredValue || requestedByUser || writesInFlight > 0) return
            request(true)
        }
        write(true, request, isMigration = true)
    }

    private suspend fun refresh(fromRoomList: Boolean) {
        val stored = matrixClient.getAccountData(SPACES_ACCOUNT_DATA_TYPE).getOrNull() ?: return
        applyStored(parseEnabled(stored), fromRoomList)
    }

    /**
     * A read that disagrees with this phone's own write is ignored while the write is in flight, and afterwards until
     * the store echoes it or the room list has restarted since the write (its first response then includes the echo).
     */
    private fun applyStored(value: Boolean, fromRoomList: Boolean) = synchronized(lock) {
        hasStoredValue = true
        if (writesInFlight > 0) return@synchronized
        val expected = unechoedWrite
        val echoMayHaveArrived = fromRoomList && restartedSinceWrite
        if (expected != null && value != expected && !echoMayHaveArrived) return@synchronized
        unechoedWrite = null
        lastStored = value
        state.value = value
    }

    private fun parseEnabled(content: String): Boolean {
        return tryOrNull {
            Json.parseToJsonElement(content).jsonObject["enabled"]?.jsonPrimitive?.booleanOrNull
        } ?: false
    }
}
