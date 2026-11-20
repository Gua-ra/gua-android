/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class GifHostRefusedException(url: HttpUrl) : IOException("Refused a GIF request to ${url.host}")

/**
 * Lets requests reach only the provider's hosts over HTTPS on 443, and follows redirects itself so a
 * redirect cannot lead anywhere else. The client must not follow redirects on its own.
 */
class GifHostAllowlist(
    private val isAllowed: (HttpUrl) -> Boolean = ::isKlipyUrl,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        repeat(MAX_REDIRECTS + 1) {
            if (!isAllowed(request.url)) throw GifHostRefusedException(request.url)
            val response = chain.proceed(request)
            val location = response.header("Location")?.takeIf { response.isRedirect } ?: return response
            response.close()
            val next = request.url.resolve(location) ?: throw IOException("Unreadable redirect")
            request = request.newBuilder().url(next).build()
        }
        throw IOException("Too many redirects")
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private val staticHost = Regex("^static[a-z0-9-]*\\.klipy\\.com$")

        fun isKlipyUrl(url: HttpUrl): Boolean = url.isHttps &&
            url.port == 443 &&
            (url.host == "api.klipy.com" || staticHost.matches(url.host))
    }
}
