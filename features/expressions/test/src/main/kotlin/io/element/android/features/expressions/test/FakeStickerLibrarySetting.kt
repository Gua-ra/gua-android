/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.test

import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerLibrary
import io.element.android.features.expressions.api.sticker.StickerLibrarySetting
import io.element.android.tests.testutils.lambda.lambdaError
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStickerLibrarySetting(
    override val library: MutableStateFlow<StickerLibrary> = MutableStateFlow(StickerLibrary.EMPTY),
    private val addResult: (StickerImage) -> Result<Unit> = { lambdaError() },
    private val favouriteResult: (StickerImage) -> Result<Unit> = { lambdaError() },
    private val unfavouriteResult: (String) -> Result<Unit> = { lambdaError() },
    private val deleteResult: (String) -> Result<Unit> = { lambdaError() },
) : StickerLibrarySetting {
    override fun preload() = Unit

    override suspend fun add(image: StickerImage): Result<Unit> = addResult(image)

    override suspend fun favourite(image: StickerImage): Result<Unit> = favouriteResult(image)

    override suspend fun unfavourite(url: String): Result<Unit> = unfavouriteResult(url)

    override suspend fun delete(shortcode: String): Result<Unit> = deleteResult(shortcode)
}
