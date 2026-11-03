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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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
        matrixClient.roomListService.syncUpdates
            .onEach { refresh(fromRoomList = false) }
            .launchIn(sessionCoroutineScope)

        // Decide the default only on Running, when another phone's value would already be stored.
        matrixClient.roomListService.state
            .onEach { if (it != RoomListService.State.Running) synchronized(lock) { restartedSinceWrite = true } }
            .filter { it == RoomListService.State.Running }
            .onEach { refresh(fromRoomList = true) }
            .launchIn(sessionCoroutineScope)
    }

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        val request = synchronized(lock) {
            requestedByUser = true
            request(enabled)
        }
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

    private suspend fun write(enabled: Boolean, request: Long, isDefault: Boolean = false): Result<Unit> = writeMutex.withLock {
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
                    // A failed default write keeps the decided default until a value is stored.
                    if (request == requestCount && (hasStoredValue || !isDefault)) {
                        state.value = lastStored
                    }
                }
        }
        result
    }

    private suspend fun storeDefaultIfUnset() {
        if (synchronized(lock) { hasStoredValue || requestedByUser || writesInFlight > 0 }) return
        val isInSpace = matrixClient.spaceService.topLevelSpaces().getOrElse { return }.isNotEmpty()
        val request = synchronized(lock) {
            if (hasStoredValue || requestedByUser || writesInFlight > 0) return
            request(isInSpace)
        }
        write(isInSpace, request, isDefault = true)
    }

    override suspend fun refresh() = refresh(fromRoomList = false)

    private suspend fun refresh(fromRoomList: Boolean) {
        val stored = matrixClient.getAccountData(SPACES_ACCOUNT_DATA_TYPE).getOrElse { return }
        when {
            stored != null -> applyStored(parseEnabled(stored), fromRoomList)
            fromRoomList -> storeDefaultIfUnset()
        }
    }

    /** Reads can be stale until this phone's write is echoed back or the room list restarts. */
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
