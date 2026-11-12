/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

class GifNetworkTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `only the provider hosts over https on 443 are allowed`() {
        assertThat(GifHostAllowlist.isKlipyUrl("https://api.klipy.com/api/v1/k/gifs/search".toHttpUrl())).isTrue()
        assertThat(GifHostAllowlist.isKlipyUrl("https://static.klipy.com/ii/a.webp".toHttpUrl())).isTrue()
        assertThat(GifHostAllowlist.isKlipyUrl("https://static2.klipy.com/ii/a.webp".toHttpUrl())).isTrue()
        assertThat(GifHostAllowlist.isKlipyUrl("http://api.klipy.com/".toHttpUrl())).isFalse()
        assertThat(GifHostAllowlist.isKlipyUrl("https://api.klipy.com:8443/".toHttpUrl())).isFalse()
        assertThat(GifHostAllowlist.isKlipyUrl("https://klipy.com/".toHttpUrl())).isFalse()
        assertThat(GifHostAllowlist.isKlipyUrl("https://static.klipy.com.example.org/".toHttpUrl())).isFalse()
        assertThat(GifHostAllowlist.isKlipyUrl("https://evil.example/static.klipy.com".toHttpUrl())).isFalse()
    }

    @Test
    fun `a request to another host never leaves the device`() {
        val client = OkHttpClient.Builder().addInterceptor(GifHostAllowlist()).build()

        assertThrows(GifHostRefusedException::class.java) {
            client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a redirect is followed within the allowed hosts and refused outside them`() {
        val allowedPath = "/allowed"
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(GifHostAllowlist(isAllowed = { it.host == server.hostName && it.port == server.port }))
            .build()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", allowedPath))
        server.enqueue(MockResponse().setBody("gif"))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://evil.example/x"))

        val followed = client.newCall(Request.Builder().url(server.url("/start")).build()).execute()

        assertThat(followed.body.string()).isEqualTo("gif")
        assertThrows(GifHostRefusedException::class.java) {
            client.newCall(Request.Builder().url(server.url("/again")).build()).execute()
        }
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `requests carry a generic user agent and nothing else of the app`() {
        server.enqueue(MockResponse())
        val client = GifHttpClient.create(GifProxy.None).okHttpClient.newBuilder()
            .apply { interceptors().removeAll { it is GifHostAllowlist } }
            .build()

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        val request = server.takeRequest()
        assertThat(request.getHeader("User-Agent")).isEqualTo(GifHttpClient.USER_AGENT)
        assertThat(request.getHeader("Authorization")).isNull()
        assertThat(request.getHeader("Cookie")).isNull()
    }

    @Test
    fun `the client never follows redirects by itself`() {
        val client = GifHttpClient.create(GifProxy.None).okHttpClient

        assertThat(client.followRedirects).isFalse()
        assertThat(client.followSslRedirects).isFalse()
        assertThat(client.proxy).isNull()
    }

    @Test
    fun `a configured proxy tunnels every request through it, unresolved`() {
        val client = GifHttpClient.create(GifProxy.Tunnel(host = "gifs.gua.global", port = 8443)).okHttpClient

        val proxy = client.proxy!!
        assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP)
        val address = proxy.address() as InetSocketAddress
        assertThat(address.isUnresolved).isTrue()
        assertThat(address.hostString).isEqualTo("gifs.gua.global")
        assertThat(address.port).isEqualTo(8443)
    }

    @Test
    fun `the proxy setting reads host and optional port`() {
        assertThat(GifProxy.parse("")).isEqualTo(GifProxy.None)
        assertThat(GifProxy.parse("  ")).isEqualTo(GifProxy.None)
        assertThat(GifProxy.parse("gifs.gua.global")).isEqualTo(GifProxy.Tunnel("gifs.gua.global", 443))
        assertThat(GifProxy.parse("gifs.gua.global:8443")).isEqualTo(GifProxy.Tunnel("gifs.gua.global", 8443))
        assertThat(GifProxy.parse(" 203.0.113.7:8443 ")).isEqualTo(GifProxy.Tunnel("203.0.113.7", 8443))
        assertThat(GifProxy.parse("[2001:db8::1]:8443")).isEqualTo(GifProxy.Tunnel("2001:db8::1", 8443))
        assertThat(GifProxy.parse("[2001:db8::1]")).isEqualTo(GifProxy.Tunnel("2001:db8::1", 443))
    }

    @Test
    fun `an unreadable proxy setting is invalid rather than ignored`() {
        listOf(
            "gifs.gua.global:0",
            "gifs.gua.global:65536",
            "gifs.gua.global:",
            "gifs.gua.global:port",
            "https://gifs.gua.global",
            "gifs.gua.global/path",
            "2001:db8::1",
            "[2001:db8::1",
            "-gifs.gua.global",
        ).forEach { value ->
            assertThat(GifProxy.parse(value)).isEqualTo(GifProxy.Invalid)
        }
    }
}
