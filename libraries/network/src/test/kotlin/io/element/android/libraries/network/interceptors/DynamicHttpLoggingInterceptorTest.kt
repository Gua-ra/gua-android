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
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
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
    fun `paths of requests not made by a Retrofit service are redacted`() {
        server.enqueue(MockResponse().setResponseCode(403))
        val request = Request.Builder().url(server.url(STATIC_MAP_PATH)).build()

        createClient(LogLevel.DEBUG).newCall(request).execute().close()

        val output = logged.joinToString("\n")
        assertThat(output).contains("--> GET ${origin()}/<redacted>")
        assertThat(output).contains("<-- 403 ${origin()}/<redacted> (")
        assertNoLocation(output)
    }

    @Test
    fun `retrofit paths built from arguments are redacted`() {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        val api = createApi(createClient(LogLevel.DEBUG))

        api.staticMap(LOCATION).execute().body()?.close()
        api.fetch(server.url(STATIC_MAP_PATH).toString()).execute().body()?.close()

        val output = logged.joinToString("\n")
        assertThat(output).contains("--> GET ${origin()}/<redacted>")
        assertThat(output).contains("<-- 204 ${origin()}/<redacted> (")
        assertNoLocation(output)
    }

    @Test
    fun `a redirect is logged at its final origin with the target path redacted`() {
        MockWebServer().use { target ->
            target.start()
            target.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", target.url(STATIC_MAP_PATH).toString()))

            createApi(createClient(LogLevel.DEBUG)).status().execute().body()?.close()

            val output = logged.joinToString("\n")
            assertThat(output).contains("--> GET ${origin()}/account/status")
            assertThat(output).contains("<-- 200 ${origin(target)}/<redacted> (after 302) (")
            assertNoLocation(output)
        }
    }

    @Test
    fun `a redirect back to the declared path keeps the path`() {
        server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", "/account/status"))
        server.enqueue(MockResponse().setResponseCode(200))

        createApi(createClient(LogLevel.DEBUG)).status().execute().body()?.close()

        assertThat(logged.joinToString("\n")).contains("<-- 200 ${origin()}/account/status (after 307) (")
    }

    @Test
    fun `failed request logs the failure without credentials`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val client = createClient(LogLevel.DEBUG).newBuilder().retryOnConnectionFailure(false).build()

        assertThrows(IOException::class.java) { verify(createApi(client)).execute() }

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
        verify(createApi(createClient(logLevel))).execute().body()?.close()
        return logged.joinToString("\n")
    }

    private fun createClient(logLevel: LogLevel): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(DynamicHttpLoggingInterceptor(InMemoryAppPreferencesStore(logLevel = logLevel)))
        .build()

    private fun createApi(client: OkHttpClient): TestApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(client)
        .build()
        .create(TestApi::class.java)

    private fun verify(api: TestApi): Call<ResponseBody> = api.verify(
        authorization = "Bearer $ACCESS_TOKEN",
        cookie = "session=$COOKIE_VALUE",
        token = X_TOKEN,
        userAgent = USER_AGENT,
        trace = QUERY_VALUE,
        body = REQUEST_BODY.toRequestBody("application/json".toMediaType()),
    )

    private fun origin(target: MockWebServer = server): String = target.url("/").toString().removeSuffix("/")

    private fun assertNoLocation(output: String) {
        val leaked = listOf(LONGITUDE, LATITUDE, "static", QUERY_VALUE).filter { it in output }
        assertThat(leaked).isEmpty()
    }

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
        const val LONGITUDE = "-73.567256"
        const val LATITUDE = "45.501690"
        const val LOCATION = "$LONGITUDE,$LATITUDE,15.0"
        const val STATIC_MAP_PATH = "/maps/basic-v2/static/$LOCATION/300x200@2x.webp?key=$QUERY_VALUE"
        const val REQUEST_BODY = """{"phone":"$PHONE","code":"$OTP_CODE","pin":"$PIN"}"""
        const val RESPONSE_BODY = """{"reauthToken":"$REAUTH_TOKEN"}"""
    }
}

private interface TestApi {
    @POST("account/reauth/verify")
    fun verify(
        @Header("Authorization") authorization: String,
        @Header("Cookie") cookie: String,
        @Header("X-Gua-Token") token: String,
        @Header("User-Agent") userAgent: String,
        @Query("trace") trace: String,
        @Body body: RequestBody,
    ): Call<ResponseBody>

    @GET("account/status")
    fun status(): Call<ResponseBody>

    @GET("maps/static/{location}/map.webp")
    fun staticMap(@Path("location") location: String): Call<ResponseBody>

    @GET
    fun fetch(@Url url: String): Call<ResponseBody>
}
