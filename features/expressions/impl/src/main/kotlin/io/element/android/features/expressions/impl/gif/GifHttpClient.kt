/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** The only client that talks to the GIF provider: API calls, previews and downloads alike. */
class GifHttpClient(factory: () -> OkHttpClient) {
    val okHttpClient: OkHttpClient by lazy(factory)

    companion object {
        const val USER_AGENT = "Gua"
        private const val TIMEOUT_SECONDS = 30L

        fun create(proxy: GifProxy): GifHttpClient = GifHttpClient {
            OkHttpClient.Builder()
                .apply {
                    if (proxy is GifProxy.Tunnel) {
                        // Unresolved, so the proxy resolves the provider's host names and the device only resolves the proxy.
                        proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxy.host, proxy.port)))
                    }
                }
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(GifHostAllowlist())
                .addInterceptor(GenericUserAgent)
                .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }
}

private object GenericUserAgent : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder().header("User-Agent", GifHttpClient.USER_AGENT).build()
    )
}
