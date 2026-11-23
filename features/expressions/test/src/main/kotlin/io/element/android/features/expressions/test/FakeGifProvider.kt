/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.test

import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifLibraryCodec
import io.element.android.features.expressions.api.gif.GifPage
import io.element.android.features.expressions.api.gif.GifProvider
import io.element.android.tests.testutils.lambda.lambdaError

class FakeGifProvider(
    override val isAvailable: Boolean = true,
    override val id: String = GifLibraryCodec.KLIPY,
    private val searchResult: (String, Int) -> Result<GifPage> = { _, _ -> lambdaError() },
    private val trendingResult: (Int) -> Result<GifPage> = { lambdaError() },
    private val itemsResult: (List<String>) -> Result<List<GifItem>> = { lambdaError() },
) : GifProvider {
    override suspend fun search(query: String, page: Int): Result<GifPage> = searchResult(query, page)

    override suspend fun trending(page: Int): Result<GifPage> = trendingResult(page)

    override suspend fun items(ids: List<String>): Result<List<GifItem>> = itemsResult(ids)
}
