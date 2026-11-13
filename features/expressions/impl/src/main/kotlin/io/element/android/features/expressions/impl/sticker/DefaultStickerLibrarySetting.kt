/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerLibrary
import io.element.android.features.expressions.api.sticker.StickerLibraryCodec
import io.element.android.features.expressions.api.sticker.StickerLibrarySetting
import io.element.android.features.expressions.impl.AccountDataCache
import io.element.android.features.expressions.impl.AccountDataDocument
import io.element.android.libraries.core.coroutine.mapState
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultStickerLibrarySetting(
    matrixClient: MatrixClient,
    cache: AccountDataCache,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
    private val clock: SystemClock,
) : StickerLibrarySetting {
    private val document = AccountDataDocument(StickerLibraryCodec.EVENT_TYPE, matrixClient, cache, sessionCoroutineScope)

    override val library: StateFlow<StickerLibrary> = document.content.mapState(StickerLibraryCodec::decode)

    override fun preload() = document.preload()

    override suspend fun add(image: StickerImage): Result<Unit> {
        val now = clock.epochMillis()
        return document.update { StickerLibraryCodec.add(it, image, now) }
    }

    override suspend fun favourite(image: StickerImage): Result<Unit> = document.update { StickerLibraryCodec.favourite(it, image) }

    override suspend fun unfavourite(url: String): Result<Unit> = document.update { StickerLibraryCodec.unfavourite(it, url) }

    override suspend fun delete(shortcode: String): Result<Unit> = document.update { StickerLibraryCodec.delete(it, shortcode) }
}
