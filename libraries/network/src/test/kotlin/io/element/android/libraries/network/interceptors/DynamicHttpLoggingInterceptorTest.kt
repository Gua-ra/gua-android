/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.interceptors

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.IOException

class DynamicHttpLoggingInterceptorTest {
    private val server = MockWebServer()
    private val logged = mutableListOf<String>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logged += message
        }
    }

    @Before
    fun setUp() {
        Timber.plant(tree)
        server.start()
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
        server.shutdown()
    }

    @Test
    fun `debug level logs method path and status with credentials and bodies redacted`() {
        val output = exchange(LogLevel.DEBUG)

        assertThat(output).contains("--> POST http://")
        assertThat(output).contains("/account/reauth/verify (")
        assertThat(output).contains("<-- 200 http://")
        assertThat(output).contains("Authorization: <redacted>")
        assertThat(output).contains("Cookie: <redacted>")
        assertThat(output).contains("X-Gua-Token: <redacted>")
        assertThat(output).contains("Set-Cookie: <redacted>")
        assertNoSecrets(output)
    }

    @Test
    fun `trace level logs the same redacted lines`() {
        val output = exchange(LogLevel.TRACE)

        assertThat(output).contains("--> POST http://")
        assertThat(output).contains("<-- 200 http://")
        assertThat(output).contains("Authorization: <redacted>")
        assertNoSecrets(output)
    }

    @Test
    fun `info level logs nothing`() {
        val output = exchange(LogLevel.INFO)

        assertThat(output).isEmpty()
    }

    @Test
    fun `error level logs nothing`() {
        val output = exchange(LogLevel.ERROR)

        assertThat(output).isEmpty()
    }

    @Test
    fun `safe headers and sizes stay visible for diagnostics`() {
        val output = exchange(LogLevel.DEBUG)

        assertThat(output).contains("User-Agent: $USER_AGENT")
        assertThat(output).contains("Content-Type: application/json")
        assertThat(output).contains("(${REQUEST_BODY.length}-byte body)")
        assertThat(output).contains("ms, ${RESPONSE_BODY.length}-byte body)")
    }

    @Test
    fun `encoded path characters are written verbatim`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val request = Request.Builder().url(server.url("/media/%20name%25?token=$QUERY_VALUE")).build()

        createClient(LogLevel.DEBUG).newCall(request).execute().close()

        val output = logged.joinToString("\n")
        assertThat(output).contains("--> GET http://")
        assertThat(output).contains("/media/%20name%25\n")
        assertThat(output).contains("<-- 204 http://")
        assertThat(output).doesNotContain(QUERY_VALUE)
    }

    @Test
    fun `failed request logs the failure without credentials`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val client = createClient(LogLevel.DEBUG).newBuilder().retryOnConnectionFailure(false).build()

        assertThrows(IOException::class.java) { client.newCall(createRequest()).execute() }

        val output = logged.joinToString("\n")
        assertThat(output).contains("--> POST http://")
        assertThat(output).contains("<-- HTTP FAILED")
        assertNoSecrets(output)
    }

    private fun exchange(logLevel: LogLevel): String {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "session=$COOKIE_VALUE; HttpOnly")
                .addHeader("Content-Type", "application/json")
                .setBody(RESPONSE_BODY)
        )
        createClient(logLevel).newCall(createRequest()).execute().use { it.body.string() }
        return logged.joinToString("\n")
    }

    private fun createClient(logLevel: LogLevel): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(DynamicHttpLoggingInterceptor(InMemoryAppPreferencesStore(logLevel = logLevel)))
        .build()

    private fun createRequest(): Request = Request.Builder()
        .url(server.url("/account/reauth/verify?trace=$QUERY_VALUE#$FRAGMENT_VALUE"))
        .header("Authorization", "Bearer $ACCESS_TOKEN")
        .header("Cookie", "session=$COOKIE_VALUE")
        .header("X-Gua-Token", X_TOKEN)
        .header("User-Agent", USER_AGENT)
        .post(REQUEST_BODY.toRequestBody("application/json".toMediaType()))
        .build()

    private fun assertNoSecrets(output: String) {
        val leaked = listOf(
            "Bearer",
            ACCESS_TOKEN,
            COOKIE_VALUE,
            X_TOKEN,
            REAUTH_TOKEN,
            PHONE,
            OTP_CODE,
            PIN,
            QUERY_VALUE,
            FRAGMENT_VALUE,
            "phone",
            "code",
            "pin",
            "reauthToken",
        ).filter { it in output }
        assertThat(leaked).isEmpty()
    }

    private companion object {
        const val USER_AGENT = "Gua/1.0 (test)"
        const val ACCESS_TOKEN = "syt_fake_access_token_0123456789"
        const val COOKIE_VALUE = "fake_cookie_abcdef"
        const val X_TOKEN = "fake_custom_token_fedcba"
        const val REAUTH_TOKEN = "fake_reauth_token_13579"
        const val PHONE = "+15550001234"
        const val OTP_CODE = "864209"
        const val PIN = "735102"
        const val QUERY_VALUE = "fake_query_secret"
        const val FRAGMENT_VALUE = "fake_fragment_secret"
        const val REQUEST_BODY = """{"phone":"$PHONE","code":"$OTP_CODE","pin":"$PIN"}"""
        const val RESPONSE_BODY = """{"reauthToken":"$REAUTH_TOKEN"}"""
    }
}
