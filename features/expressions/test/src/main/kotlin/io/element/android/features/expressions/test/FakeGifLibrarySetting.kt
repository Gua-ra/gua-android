/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.test

import io.element.android.features.expressions.api.gif.GifLibrary
import io.element.android.features.expressions.api.gif.GifLibrarySetting
import io.element.android.tests.testutils.lambda.lambdaError
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGifLibrarySetting(
    override val library: MutableStateFlow<GifLibrary> = MutableStateFlow(GifLibrary.EMPTY),
    private val favouriteResult: (String) -> Result<Unit> = { lambdaError() },
    private val unfavouriteResult: (String) -> Result<Unit> = { lambdaError() },
) : GifLibrarySetting {
    override fun preload() = Unit

    override suspend fun favourite(id: String): Result<Unit> = favouriteResult(id)

    override suspend fun unfavourite(id: String): Result<Unit> = unfavouriteResult(id)
}
