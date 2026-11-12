/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.flow.first
import timber.log.Timber

interface AccountDataCache {
    suspend fun load(eventType: String): String?

    suspend fun save(eventType: String, content: String)
}

@ContributesBinding(SessionScope::class)
class DataStoreAccountDataCache(
    private val preferences: ExpressionsPreferences,
) : AccountDataCache {
    override suspend fun load(eventType: String): String? = runCatchingExceptions { preferences.store.data.first()[key(eventType)] }.getOrNull()

    override suspend fun save(eventType: String, content: String) {
        runCatchingExceptions { preferences.store.edit { it[key(eventType)] = content } }
            .onFailure { Timber.e(it, "Failed to cache $eventType") }
    }

    private fun key(eventType: String) = stringPreferencesKey("account_data:$eventType")
}
