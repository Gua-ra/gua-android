/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.gif.RecentGifsStore
import io.element.android.features.expressions.api.sticker.RecentStickersStore
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.impl.gif.DataStoreRecentGifsStore
import io.element.android.features.expressions.impl.sticker.DataStoreRecentStickersStore
import io.element.android.libraries.matrix.api.paths.SessionPaths
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentsStoresTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `a sent sticker moves to the front and keeps its info`() = runTest {
        val store = DataStoreRecentStickersStore(aPreferences())
        val info = buildJsonObject { put("w", JsonPrimitive(128)) }

        store.add(aSticker("a"))
        store.add(aSticker("b").copy(info = info))
        store.add(aSticker("a"))

        assertThat(store.recents.first()).containsExactly(aSticker("a"), aSticker("b").copy(info = info)).inOrder()
    }

    @Test
    fun `recent stickers keep the newest thirty and can be removed`() = runTest {
        val store = DataStoreRecentStickersStore(aPreferences())

        repeat(RecentStickersStore.MAX_RECENTS + 2) { store.add(aSticker("s$it")) }
        store.remove("mxc://gua.global/s31")

        val recents = store.recents.first()
        assertThat(recents).hasSize(RecentStickersStore.MAX_RECENTS - 1)
        assertThat(recents.first().url).isEqualTo("mxc://gua.global/s30")
        assertThat(recents.last().url).isEqualTo("mxc://gua.global/s2")
    }

    @Test
    fun `recent GIFs keep the newest thirty ids, newest first`() = runTest {
        val store = DataStoreRecentGifsStore(aPreferences())

        repeat(RecentGifsStore.MAX_RECENTS + 1) { store.add("g$it") }
        store.add("g5")
        store.remove("g30")

        val recents = store.recents.first()
        assertThat(recents).hasSize(RecentGifsStore.MAX_RECENTS - 1)
        assertThat(recents.take(2)).containsExactly("g5", "g29").inOrder()
    }

    @Test
    fun `both lists share one file without overwriting each other`() = runTest {
        val preferences = aPreferences()
        val stickers = DataStoreRecentStickersStore(preferences)
        val gifs = DataStoreRecentGifsStore(preferences)

        stickers.add(aSticker("a"))
        gifs.add("g")

        assertThat(stickers.recents.first()).containsExactly(aSticker("a"))
        assertThat(gifs.recents.first()).containsExactly("g")
    }

    private fun TestScope.aPreferences(): ExpressionsPreferences {
        val directory = temporaryFolder.newFolder()
        return ExpressionsPreferences(
            matrixClient = FakeMatrixClient(sessionPaths = SessionPaths(fileDirectory = directory, cacheDirectory = directory)),
            sessionCoroutineScope = backgroundScope,
        )
    }

    private fun aSticker(name: String) = StickerImage(url = "mxc://gua.global/$name", body = name, info = null)
}
