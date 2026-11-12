/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifLibraryCodec
import io.element.android.features.expressions.api.gif.GifPage
import io.element.android.features.expressions.api.gif.GifProvider
import io.element.android.features.expressions.api.gif.GifsUnavailableException
import io.element.android.features.expressions.api.parseJsonObject
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

/** KLIPY's native API. Requests carry no customer id and nothing about the user. */
class KlipyGifProvider(
    private val config: GifConfig,
    private val httpClient: GifHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val locale: () -> String = ::deviceLocale,
    private val baseUrl: HttpUrl = BASE_URL.toHttpUrl(),
) : GifProvider {
    override val id: String = GifLibraryCodec.KLIPY

    override val isAvailable: Boolean get() = config.isAvailable

    override suspend fun search(query: String, page: Int): Result<GifPage> = fetchPage(page) {
        addPathSegment("search").addQueryParameter("q", query)
    }

    override suspend fun trending(page: Int): Result<GifPage> = fetchPage(page) {
        addPathSegment("trending")
    }

    override suspend fun items(ids: List<String>): Result<List<GifItem>> {
        if (ids.isEmpty()) return Result.success(emptyList())
        val url = endpoint()
            .addPathSegment("items")
            .addEncodedQueryParameter("slugs", ids.joinToString(",") { URLEncoder.encode(it, Charsets.UTF_8.name()) })
            .build()
        return fetch(url, requestedPage = 1).map { page ->
            val byId = page.items.associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }
    }

    private suspend fun fetchPage(page: Int, path: HttpUrl.Builder.() -> HttpUrl.Builder): Result<GifPage> {
        val url = endpoint()
            .path()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter("locale", locale())
            .addQueryParameter("content_filter", "medium")
            .addEncodedQueryParameter("format_filter", "webp,jpg")
            .build()
        return fetch(url, requestedPage = page)
    }

    private suspend fun fetch(url: HttpUrl, requestedPage: Int): Result<GifPage> {
        if (!isAvailable) return Result.failure(GifsUnavailableException())
        return withContext(ioDispatcher) {
            runCatchingExceptions {
                httpClient.okHttpClient.newCall(Request.Builder().url(url).build()).await().use { response ->
                    if (!response.isSuccessful) throw IOException("KLIPY answered ${response.code}")
                    KlipyJson.page(parseJsonObject(response.body.string()), requestedPage)
                }
            }
        }
    }

    private fun endpoint(): HttpUrl.Builder = baseUrl.newBuilder()
        .addPathSegment(config.apiKey)
        .addPathSegment("gifs")

    companion object {
        const val BASE_URL = "https://api.klipy.com/api/v1/"
        const val PAGE_SIZE = 30
    }
}

internal fun deviceLocale(): String {
    val locale = Locale.getDefault()
    return locale.country.lowercase(Locale.ROOT).ifEmpty { locale.language.lowercase(Locale.ROOT) }
}
