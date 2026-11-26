/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.features.expressions.api.gif.GifProvider
import io.element.android.features.expressions.impl.BuildConfig
import io.element.android.features.expressions.impl.gif.GifConfig
import io.element.android.features.expressions.impl.gif.GifHttpClient
import io.element.android.features.expressions.impl.gif.GifProxy
import io.element.android.features.expressions.impl.gif.KlipyGifProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers

@BindingContainer
@ContributesTo(AppScope::class)
object GifBindingContainer {
    @Provides
    fun provideGifConfig(): GifConfig = GifConfig(
        apiKey = BuildConfig.KLIPY_API_KEY,
        proxy = GifProxy.parse(BuildConfig.GIF_PROXY_HOST),
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideGifHttpClient(config: GifConfig): GifHttpClient = GifHttpClient.create(config.proxy)

    @Provides
    @SingleIn(AppScope::class)
    fun provideGifProvider(config: GifConfig, httpClient: GifHttpClient, dispatchers: CoroutineDispatchers): GifProvider =
        KlipyGifProvider(config = config, httpClient = httpClient, ioDispatcher = dispatchers.io)
}
