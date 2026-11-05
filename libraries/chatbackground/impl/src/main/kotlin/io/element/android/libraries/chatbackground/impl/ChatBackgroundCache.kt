/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

interface ChatBackgroundCache {
    suspend fun load(): Map<RoomId, StoredChatBackground>

    /** Stores [stored] as the most recently used entry. */
    suspend fun save(roomId: RoomId, stored: StoredChatBackground)
}

// In the session's own directory, which every sign-out deletes with the rest of the session's data.
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DataStoreChatBackgroundCache(
    matrixClient: MatrixClient,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
) : ChatBackgroundCache {
    private val store = PreferenceDataStoreFactory.create(scope = sessionCoroutineScope) {
        File(matrixClient.sessionPaths.fileDirectory, "chat-backgrounds.preferences_pb")
    }
    private val entriesKey = stringPreferencesKey("entries")

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
                preferences[entriesKey] = Json.encodeToString(updated.take(MAX_ROOMS))
            }
        }
    }

    private companion object {
        const val MAX_ROOMS = 500
    }
}

@Serializable
private data class CacheEntry(
    val roomId: String,
    val stored: StoredChatBackground,
)
