/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.test

import io.element.android.features.expressions.api.sticker.RecentStickersStore
import io.element.android.features.expressions.api.sticker.StickerImage
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRecentStickersStore(
    override val recents: MutableStateFlow<List<StickerImage>> = MutableStateFlow(emptyList()),
) : RecentStickersStore {
    override suspend fun add(image: StickerImage) {
        recents.value = (listOf(image) + recents.value.filterNot { it.url == image.url }).take(RecentStickersStore.MAX_RECENTS)
    }

    override suspend fun remove(url: String) {
        recents.value = recents.value.filterNot { it.url == url }
    }
}
