/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.androidutils.file.safeDelete
import io.element.android.libraries.androidutils.hash.hash
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.sessionstorage.api.observer.SessionListener
import io.element.android.libraries.sessionstorage.api.observer.SessionObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface ChatBackgroundCache {
    suspend fun load(): Map<RoomId, StoredChatBackground>

    /** Stores [stored] as the most recently used entry, dropping the least recently used beyond [MAX_ROOMS]. */
    suspend fun save(roomId: RoomId, stored: StoredChatBackground)

    companion object {
        const val MAX_ROOMS = 500
    }
}

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DataStoreChatBackgroundCache(
    @ApplicationContext context: Context,
    matrixClient: MatrixClient,
    sessionObserver: SessionObserver,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
) : ChatBackgroundCache {
    private val sessionId = matrixClient.sessionId
    private val dataStoreFile = context.preferencesDataStoreFile("session_${sessionId.value.hash().take(16)}_chat-backgrounds")
    private val store = PreferenceDataStoreFactory.create(scope = sessionCoroutineScope) { dataStoreFile }
    private val entriesKey = stringPreferencesKey("entries")

    init {
        sessionObserver.addListener(object : SessionListener {
            override suspend fun onSessionDeleted(userId: String, wasLastSession: Boolean) {
                if (userId == sessionId.value) {
                    dataStoreFile.safeDelete()
                }
            }
        })
    }

    override suspend fun load(): Map<RoomId, StoredChatBackground> {
        val json = tryOrNull { store.data.first()[entriesKey] } ?: return emptyMap()
        val entries = tryOrNull { Json.decodeFromString<List<CacheEntry>>(json) }.orEmpty()
        return entries.mapNotNull { entry -> tryOrNull { RoomId(entry.roomId) }?.let { it to entry.stored } }.toMap()
    }

    override suspend fun save(roomId: RoomId, stored: StoredChatBackground) {
        tryOrNull {
            store.edit { preferences ->
                val entries = preferences[entriesKey]?.let { tryOrNull { Json.decodeFromString<List<CacheEntry>>(it) } }.orEmpty()
                val updated = listOf(CacheEntry(roomId.value, stored)) + entries.filter { it.roomId != roomId.value }
                preferences[entriesKey] = Json.encodeToString(updated.take(ChatBackgroundCache.MAX_ROOMS))
            }
        }
    }
}

@Serializable
private data class CacheEntry(
    val roomId: String,
    val stored: StoredChatBackground,
)
