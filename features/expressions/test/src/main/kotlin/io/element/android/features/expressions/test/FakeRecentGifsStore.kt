/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.test

import io.element.android.features.expressions.api.gif.RecentGifsStore
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRecentGifsStore(
    override val recents: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
) : RecentGifsStore {
    override suspend fun add(id: String) {
        recents.value = (listOf(id) + recents.value.filterNot { it == id }).take(RecentGifsStore.MAX_RECENTS)
    }

    override suspend fun remove(id: String) {
        recents.value = recents.value.filterNot { it == id }
    }
}
