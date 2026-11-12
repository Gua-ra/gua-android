/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.gif.GifFile
import io.element.android.features.expressions.api.gif.GifRendition
import io.element.android.features.expressions.api.gif.GifSize
import io.element.android.features.expressions.api.gif.GifsUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class KlipyGifProviderTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a search asks for one page of webp and jpg renditions in the device region`() = runTest {
        server.enqueue(MockResponse().setBody(A_SEARCH_RESPONSE))

        createProvider().search("good morning", page = 2).getOrThrow()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.requestUrl!!.encodedPath).isEqualTo("/api/v1/a-key/gifs/search")
        assertThat(request.requestUrl!!.queryParameter("q")).isEqualTo("good morning")
        assertThat(request.requestUrl!!.queryParameter("page")).isEqualTo("2")
        assertThat(request.requestUrl!!.queryParameter("per_page")).isEqualTo("30")
        assertThat(request.requestUrl!!.queryParameter("locale")).isEqualTo("br")
        assertThat(request.requestUrl!!.queryParameter("content_filter")).isEqualTo("medium")
        assertThat(request.requestUrl!!.encodedQuery).contains("format_filter=webp,jpg")
        assertThat(request.requestUrl!!.queryParameterNames).doesNotContain("customer_id")
    }

    @Test
    fun `a page keeps the provider's order, skips other types and says whether there is more`() = runTest {
        server.enqueue(MockResponse().setBody(A_SEARCH_RESPONSE))

        val page = createProvider().search("hi", page = 1).getOrThrow()

        assertThat(page.items.map { it.id }).containsExactly("hello-hi-662", "wave-12").inOrder()
        assertThat(page.hasNext).isTrue()
        assertThat(page.page).isEqualTo(1)
        val hello = page.items.first()
        assertThat(hello.title).isEqualTo("Hello")
        assertThat(hello.renditions[GifSize.Sm]).isEqualTo(
            GifRendition(
                animated = GifFile(url = "https://static.klipy.com/ii/a/sm.webp", width = 220, height = 220, size = 25_340),
                still = GifFile(url = "https://static.klipy.com/ii/a/sm.jpg", width = 220, height = 220, size = 8560),
            )
        )
        assertThat(hello.preview?.url).isEqualTo("https://static.klipy.com/ii/a/sm.webp")
    }

    @Test
    fun `trending uses the trending endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"result":true,"data":{"data":[],"current_page":1,"per_page":30,"has_next":false}}"""))

        val page = createProvider().trending(page = 1).getOrThrow()

        assertThat(server.takeRequest().requestUrl!!.encodedPath).isEqualTo("/api/v1/a-key/gifs/trending")
        assertThat(page.items).isEmpty()
        assertThat(page.hasNext).isFalse()
    }

    @Test
    fun `items are looked up by slug and returned in the order asked for`() = runTest {
        server.enqueue(MockResponse().setBody(A_SEARCH_RESPONSE))

        val items = createProvider().items(listOf("wave-12", "gone-1", "hello-hi-662")).getOrThrow()

        val request = server.takeRequest()
        assertThat(request.requestUrl!!.encodedPath).isEqualTo("/api/v1/a-key/gifs/items")
        assertThat(request.requestUrl!!.encodedQuery).isEqualTo("slugs=wave-12,gone-1,hello-hi-662")
        assertThat(items.map { it.id }).containsExactly("wave-12", "hello-hi-662").inOrder()
    }

    @Test
    fun `without a key nothing is requested`() = runTest {
        val provider = createProvider(config = GifConfig(apiKey = "", proxy = GifProxy.None))

        assertThat(provider.isAvailable).isFalse()
        assertThat(provider.search("hi", page = 1).exceptionOrNull()).isInstanceOf(GifsUnavailableException::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `an unreadable proxy makes GIFs unavailable`() = runTest {
        val provider = createProvider(config = GifConfig(apiKey = "a-key", proxy = GifProxy.Invalid))

        assertThat(provider.isAvailable).isFalse()
    }

    @Test
    fun `an error status is a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        assertThat(createProvider().trending(page = 1).isFailure).isTrue()
    }

    private fun createProvider(config: GifConfig = GifConfig(apiKey = "a-key", proxy = GifProxy.None)) = KlipyGifProvider(
        config = config,
        httpClient = GifHttpClient { OkHttpClient() },
        ioDispatcher = Dispatchers.IO,
        locale = { "br" },
        baseUrl = server.url("/api/v1/"),
    )

    private companion object {
        fun anItem(id: Long, slug: String, title: String, type: String = "gif", base: String = "https://static.klipy.com/ii/a") = """
            {
              "id": $id,
              "slug": "$slug",
              "title": "$title",
              "file": {
                "hd": {
                  "gif": {"url": "$base/hd.gif", "width": 498, "height": 498, "size": 2100000},
                  "webp": {"url": "$base/hd.webp", "width": 498, "height": 498, "size": 288000},
                  "jpg": {"url": "$base/hd.jpg", "width": 498, "height": 498, "size": 30000},
                  "mp4": {"url": "$base/hd.mp4", "width": 498, "height": 498, "size": 295000}
                },
                "md": {
                  "webp": {"url": "$base/md.webp", "width": 320, "height": 320, "size": 120000},
                  "jpg": {"url": "$base/md.jpg", "width": 320, "height": 320, "size": 16000}
                },
                "sm": {
                  "webp": {"url": "$base/sm.webp", "width": 220, "height": 220, "size": 25340},
                  "jpg": {"url": "$base/sm.jpg", "width": 220, "height": 220, "size": 8560}
                },
                "xs": {
                  "webp": {"url": "$base/xs.webp", "width": 90, "height": 90, "size": 9000},
                  "jpg": {"url": "$base/xs.jpg", "width": 90, "height": 90, "size": 2949}
                }
              },
              "tags": ["hello"],
              "type": "$type"
            }
        """.trimIndent()

        val A_SEARCH_RESPONSE = """
            {
              "result": true,
              "data": {
                "data": [
                  ${anItem(8_041_071_659_142_944, "hello-hi-662", "Hello")},
                  ${anItem(7, "an-ad", "Ad", type = "ad")},
                  ${anItem(12, "wave-12", "Wave", base = "https://static2.klipy.com/ii/b")}
                ],
                "current_page": 1,
                "per_page": 30,
                "has_next": true
              }
            }
        """.trimIndent()
    }
}
