/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Not a secret. Both calls throw an IOException when the store cannot be read or written. */
internal class ResolverCheckpointPin(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun read(resolverBaseUrl: String): LogCheckpoint? {
        val preferences = dataStore.data.first()
        val merkleRoot = preferences[rootKey(resolverBaseUrl)] ?: return null
        val size = preferences[sizeKey(resolverBaseUrl)] ?: return null
        return LogCheckpoint(merkleRoot = merkleRoot, size = size)
    }

    suspend fun advance(resolverBaseUrl: String, checkpoint: LogCheckpoint) {
        dataStore.edit { preferences ->
            val pinnedSize = preferences[sizeKey(resolverBaseUrl)]
            if (pinnedSize == null || pinnedSize < checkpoint.size) {
                preferences[rootKey(resolverBaseUrl)] = checkpoint.merkleRoot
                preferences[sizeKey(resolverBaseUrl)] = checkpoint.size
            }
        }
    }

    private fun rootKey(resolverBaseUrl: String) = stringPreferencesKey("checkpoint_root:$resolverBaseUrl")

    private fun sizeKey(resolverBaseUrl: String) = longPreferencesKey("checkpoint_size:$resolverBaseUrl")
}
