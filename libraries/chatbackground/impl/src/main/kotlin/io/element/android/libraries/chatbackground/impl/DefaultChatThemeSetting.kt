/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.chatbackground.api.ChatBackgroundCodec
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.chatbackground.api.ChatThemeSetting
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
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
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

internal const val CHAT_THEME_ACCOUNT_DATA_TYPE = "global.gua.chat_theme"
internal val CHAT_THEME_REFRESH_INTERVAL = 5.seconds

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultChatThemeSetting(
    private val matrixClient: MatrixClient,
    private val cache: ChatBackgroundCache,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : ChatThemeSetting {
    private val state = MutableStateFlow(ChatTheme.STOCK)
    override val theme: StateFlow<ChatTheme> = state.asStateFlow()

    private val started = AtomicBoolean(false)
    private val writeMutex = Mutex()
    private val lock = Any()

    // Guarded by lock.
    private var requestCount = 0L
    private var writesInFlight = 0
    private var unechoedWrite: ChatTheme? = null
    private var restartedSinceWrite = false
    private var lastStored = ChatTheme.STOCK
    private var isKnown = false

    override fun preload() {
        if (!started.compareAndSet(false, true)) return
        sessionCoroutineScope.launch {
            val cached = cache.loadDefaultTheme()?.let(ChatBackgroundCodec::decode)
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
                delay(CHAT_THEME_REFRESH_INTERVAL)
            }
        }
    }

    override suspend fun set(theme: ChatTheme): Result<Unit> {
        val request = synchronized(lock) {
            requestCount++
            writesInFlight++
            isKnown = true
            state.value = theme
            requestCount
        }
        return sessionCoroutineScope.async { write(theme, request) }.await()
    }

    private suspend fun write(theme: ChatTheme, request: Long): Result<Unit> = writeMutex.withLock {
        val content = ChatBackgroundCodec.encode(theme)
        // Clearing writes {}: MSC3391 deletion is off on Gua's servers.
        val result = matrixClient.setAccountData(CHAT_THEME_ACCOUNT_DATA_TYPE, content)
        synchronized(lock) {
            writesInFlight--
            result
                .onSuccess {
                    unechoedWrite = theme
                    restartedSinceWrite = false
                    lastStored = theme
                }
                .onFailure {
                    Timber.e(it, "Failed to store the default chat theme")
                    if (request == requestCount) state.value = lastStored
                }
        }
        if (result.isSuccess) cache.saveDefaultTheme(content)
        result
    }

    private suspend fun refresh(isAfterSync: Boolean) {
        val content = matrixClient.getAccountData(CHAT_THEME_ACCOUNT_DATA_TYPE).getOrElse { return }
        // Before a sync the store may have been cleared, so only a stored value replaces the cached one.
        if (content == null && !isAfterSync) return
        val stored = ChatBackgroundCodec.decode(content)
        if (applyStored(stored)) cache.saveDefaultTheme(ChatBackgroundCodec.encode(stored))
    }

    /** Reads can be stale until this device's write is echoed back or the room list restarts. */
    private fun applyStored(value: ChatTheme): Boolean = synchronized(lock) {
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
}
