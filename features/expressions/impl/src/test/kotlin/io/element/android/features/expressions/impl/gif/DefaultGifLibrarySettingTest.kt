/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.gif.GifFavourite
import io.element.android.features.expressions.api.gif.GifLibraryCodec
import io.element.android.features.expressions.api.parseJsonObject
import io.element.android.features.expressions.impl.InMemoryAccountDataCache
import io.element.android.features.expressions.test.FakeGifProvider
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultGifLibrarySettingTest {
    @Test
    fun `the cached favourites show before the store is read`() = runTest {
        val cache = InMemoryAccountDataCache(mapOf(GifLibraryCodec.EVENT_TYPE to """{"favourites":[{"id":"a","p":"klipy"}],"v":1}"""))
        val setting = createSetting(cache = cache, readResult = { Result.failure(Exception("store")) })

        setting.preload()
        runCurrent()

        assertThat(setting.library.value.favourites).containsExactly(GifFavourite(id = "a", provider = "klipy"))
    }

    @Test
    fun `favouriting writes the provider id and keeps other providers`() = runTest {
        val writes = mutableListOf<Pair<String, String>>()
        val setting = createSetting(stored = { """{"favourites":[{"id":"a","p":"other"}],"v":1}""" }, writes = writes)
        setting.preload()
        runCurrent()

        setting.favourite("b")
        setting.unfavourite("a")

        assertThat(writes.map { it.first }).containsExactly(GifLibraryCodec.EVENT_TYPE, GifLibraryCodec.EVENT_TYPE)
        assertThat(parseJsonObject(writes.last().second))
            .isEqualTo(parseJsonObject("""{"favourites":[{"id":"b","p":"klipy"},{"id":"a","p":"other"}],"v":1}"""))
    }

    @Test
    fun `a failed write puts the stored favourites back`() = runTest {
        val setting = createSetting(stored = { """{"favourites":[{"id":"a","p":"klipy"}],"v":1}""" }, writeResult = { Result.failure(Exception("offline")) })
        setting.preload()
        runCurrent()

        assertThat(setting.unfavourite("a").isFailure).isTrue()

        assertThat(setting.library.value.isFavourite("a")).isTrue()
    }

    private fun TestScope.createSetting(
        cache: InMemoryAccountDataCache = InMemoryAccountDataCache(),
        stored: () -> String? = { null },
        readResult: () -> Result<String?> = { Result.success(stored()) },
        writes: MutableList<Pair<String, String>> = mutableListOf(),
        writeResult: () -> Result<Unit> = { Result.success(Unit) },
    ) = DefaultGifLibrarySetting(
        matrixClient = FakeMatrixClient(
            getAccountDataLambda = { type ->
                assertThat(type).isEqualTo(GifLibraryCodec.EVENT_TYPE)
                readResult()
            },
            setAccountDataLambda = { type, content ->
                writes += type to content
                writeResult()
            },
        ),
        cache = cache,
        sessionCoroutineScope = backgroundScope,
        gifProvider = FakeGifProvider(),
    )
}
