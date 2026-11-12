/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.api.sticker.RecentStickersStore
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.impl.ExpressionsPreferences
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import timber.log.Timber

@ContributesBinding(SessionScope::class)
class DataStoreRecentStickersStore(
    private val preferences: ExpressionsPreferences,
) : RecentStickersStore {
    private val key = stringPreferencesKey("recent_stickers")

    override val recents: Flow<List<StickerImage>> = preferences.store.data
        .map { decode(it[key]) }
        .catch { emit(emptyList()) }

    override suspend fun add(image: StickerImage) = update { recents ->
        (listOf(image) + recents.filterNot { it.url == image.url }).take(RecentStickersStore.MAX_RECENTS)
    }

    override suspend fun remove(url: String) = update { recents -> recents.filterNot { it.url == url } }

    private suspend fun update(transform: (List<StickerImage>) -> List<StickerImage>) {
        runCatchingExceptions {
            preferences.store.edit { it[key] = encode(transform(decode(it[key]))) }
        }.onFailure { Timber.e(it, "Failed to store the recents") }
    }

    private fun decode(json: String?): List<StickerImage> {
        val array = json?.let { tryOrNull { Json.parseToJsonElement(it) } } as? JsonArray ?: return emptyList()
        return array.mapNotNull { StickerImage.fromJson(it) }
    }

    private fun encode(recents: List<StickerImage>): String = JsonArray(recents.map { it.toJson() }).toString()
}
