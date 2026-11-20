/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.expressions.api.gif.GifLibrary
import io.element.android.features.expressions.api.gif.GifLibraryCodec
import io.element.android.features.expressions.api.gif.GifLibrarySetting
import io.element.android.features.expressions.api.gif.GifProvider
import io.element.android.features.expressions.impl.AccountDataCache
import io.element.android.features.expressions.impl.AccountDataDocument
import io.element.android.libraries.core.coroutine.mapState
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultGifLibrarySetting(
    matrixClient: MatrixClient,
    cache: AccountDataCache,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
    private val gifProvider: GifProvider,
) : GifLibrarySetting {
    private val document = AccountDataDocument(GifLibraryCodec.EVENT_TYPE, matrixClient, cache, sessionCoroutineScope)

    override val library: StateFlow<GifLibrary> = document.content.mapState(GifLibraryCodec::decode)

    override fun preload() = document.preload()

    override suspend fun favourite(id: String): Result<Unit> = document.update { GifLibraryCodec.favourite(it, id, gifProvider.id) }

    override suspend fun unfavourite(id: String): Result<Unit> = document.update { GifLibraryCodec.unfavourite(it, id, gifProvider.id) }
}
