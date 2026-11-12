/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl

import io.element.android.features.expressions.api.parseJsonObject
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

internal val ACCOUNT_DATA_REFRESH_INTERVAL = 5.seconds

/**
 * One global account data event, kept as its raw JSON so edits keep what this version does not know.
 * It is read like `global.gua.chat_theme`: from the cache, then the store at start and after syncs,
 * with optimistic edits that roll back when the write fails.
 */
internal class AccountDataDocument(
    private val eventType: String,
    private val matrixClient: MatrixClient,
    private val cache: AccountDataCache,
    private val sessionCoroutineScope: CoroutineScope,
) {
    private val state = MutableStateFlow(EMPTY)
    val content: StateFlow<JsonObject> = state.asStateFlow()

    private val started = AtomicBoolean(false)
    private val writeMutex = Mutex()
    private val lock = Any()

    // Guarded by lock.
    private var requestCount = 0L
    private var writesInFlight = 0
    private var unechoedWrite: JsonObject? = null
    private var restartedSinceWrite = false
    private var lastStored = EMPTY
    private var isKnown = false

    fun preload() {
        if (!started.compareAndSet(false, true)) return
        sessionCoroutineScope.launch {
            val cached = cache.load(eventType)?.let(::parseJsonObject)
            synchronized(lock) {
                if (!isKnown && cached != null) {
                    lastStored = cached
                    state.value = cached
                }
            }
            refresh(isAfterSync = false)
        }
        val roomListService = matrixClient.roomListService
        // The SDK cannot observe a custom account data type, so the local store is read again after syncs.
        val refreshRequests = Channel<Unit>(Channel.CONFLATED)
        merge(
            roomListService.syncUpdates,
            roomListService.state
                .onEach { if (it != RoomListService.State.Running) synchronized(lock) { restartedSinceWrite = true } }
                .filter { it == RoomListService.State.Running }
                .map { },
        )
            .onEach { refreshRequests.trySend(Unit) }
            .launchIn(sessionCoroutineScope)
        sessionCoroutineScope.launch {
            refreshRequests.consumeEach {
                refresh(isAfterSync = true)
                delay(ACCOUNT_DATA_REFRESH_INTERVAL)
            }
        }
    }

    /** Shows the edit at once; a failed write puts the stored content back. [edit] may throw to refuse. */
    suspend fun update(edit: (JsonObject) -> JsonObject): Result<Unit> {
        val (content, request) = synchronized(lock) {
            val content = runCatchingExceptions { edit(state.value) }.getOrElse { return Result.failure(it) }
            requestCount++
            writesInFlight++
            isKnown = true
            state.value = content
            content to requestCount
        }
        return sessionCoroutineScope.async { write(content, request) }.await()
    }

    private suspend fun write(content: JsonObject, request: Long): Result<Unit> = writeMutex.withLock {
        val json = content.toString()
        // Clearing writes {}: MSC3391 deletion is off on Gua's servers.
        val result = matrixClient.setAccountData(eventType, json)
        synchronized(lock) {
            writesInFlight--
            result
                .onSuccess {
                    unechoedWrite = content
                    restartedSinceWrite = false
                    lastStored = content
                }
                .onFailure {
                    Timber.e(it, "Failed to store $eventType")
                    if (request == requestCount) state.value = lastStored
                }
        }
        if (result.isSuccess) cache.save(eventType, json)
        result
    }

    private suspend fun refresh(isAfterSync: Boolean) {
        val json = matrixClient.getAccountData(eventType).getOrElse { return }
        // Before a sync the store may have been cleared, so only a stored value replaces the cached one.
        if (json == null && !isAfterSync) return
        val stored = parseJsonObject(json)
        if (applyStored(stored)) cache.save(eventType, stored.toString())
    }

    /** Reads can be stale until this device's write is echoed back or the room list restarts. */
    private fun applyStored(value: JsonObject): Boolean = synchronized(lock) {
        if (writesInFlight > 0) return false
        val expected = unechoedWrite
        val echoMayHaveArrived = restartedSinceWrite && matrixClient.roomListService.state.value == RoomListService.State.Running
        if (expected != null && value != expected && !echoMayHaveArrived) return false
        unechoedWrite = null
        isKnown = true
        val changed = value != lastStored
        lastStored = value
        state.value = value
        changed
    }

    private companion object {
        val EMPTY = JsonObject(emptyMap())
    }
}
