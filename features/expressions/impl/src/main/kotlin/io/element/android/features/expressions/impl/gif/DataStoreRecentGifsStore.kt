/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.api.gif.RecentGifsStore
import io.element.android.features.expressions.impl.ExpressionsPreferences
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

@ContributesBinding(SessionScope::class)
class DataStoreRecentGifsStore(
    private val preferences: ExpressionsPreferences,
) : RecentGifsStore {
    private val key = stringPreferencesKey("recent_gifs")

    override val recents: Flow<List<String>> = preferences.store.data
        .map { decode(it[key]) }
        .catch { emit(emptyList()) }

    override suspend fun add(id: String) = update { recents ->
        (listOf(id) + recents.filterNot { it == id }).take(RecentGifsStore.MAX_RECENTS)
    }

    override suspend fun remove(id: String) = update { recents -> recents.filterNot { it == id } }

    private suspend fun update(transform: (List<String>) -> List<String>) {
        runCatchingExceptions {
            preferences.store.edit { it[key] = encode(transform(decode(it[key]))) }
        }.onFailure { Timber.e(it, "Failed to store the recents") }
    }

    private fun decode(json: String?): List<String> {
        val array = json?.let { tryOrNull { Json.parseToJsonElement(it) } } as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonPrimitive)?.takeIf { id -> id.isString }?.content }
    }

    private fun encode(recents: List<String>): String = JsonArray(recents.map(::JsonPrimitive)).toString()
}
