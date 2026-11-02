/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.guaresolver.FakeEnrollmentRedirectProvider
import io.element.android.libraries.guaresolver.FakeGuaDeployment
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import io.element.android.libraries.network.useragent.UserAgentProvider
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class DefaultIdentityServiceClientLookupLoggingTest {
    private val server = MockWebServer()
    private val interceptedPaths = mutableListOf<String>()
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
    fun `the contact lookup reaches no app interceptor except the User-Agent one`() = runTest {
        server.enqueue(MockResponse().setBody("""{ "matches": [] }"""))
        server.enqueue(MockResponse().setResponseCode(204))
        val client = createClient()

        client.lookupContacts("secret-token", listOf(A_PHONE)).getOrThrow()
        client.cancelAccountRecovery("secret-token").getOrThrow()

        assertThat(interceptedPaths).containsExactly(
            "application /security/recovery/cancel",
            "network /security/recovery/cancel",
        ).inOrder()
        val lookup = server.takeRequest()
        assertThat(lookup.path).isEqualTo("/directory/lookup")
        assertThat(lookup.getHeader("User-Agent")).isEqualTo(A_USER_AGENT)
    }

    @Test
    fun `a malformed lookup response is logged without its content`() = runTest {
        server.enqueue(MockResponse().setBody("""{ "matches": [ { "phone": "$A_PHONE", "userId": "@alice:gua.global" }, 7 ] }"""))
        val client = createClient()

        val result = client.lookupContacts("secret-token", listOf(A_PHONE))

        assertThat(result.exceptionOrNull()).isInstanceOf(ResolverError.Transport::class.java)
        assertThat(logged).isNotEmpty()
        logged.forEach { line ->
            assertThat(line).doesNotContain(A_PHONE.drop(1))
            assertThat(line).doesNotContain("alice")
        }
    }

    private fun createClient() = DefaultIdentityServiceClient(
        retrofitFactory = RetrofitFactory(
            okHttpClient = {
                OkHttpClient.Builder()
                    .addInterceptor(UserAgentInterceptor(FixedUserAgentProvider))
                    .addInterceptor(recordingInterceptor("application"))
                    .addNetworkInterceptor(recordingInterceptor("network"))
                    .build()
            },
            json = { DefaultJsonProvider() },
        ),
        enrollmentRedirectProvider = FakeEnrollmentRedirectProvider(),
        deployment = FakeGuaDeployment(identityServiceBaseUrl = server.url("/").toString()),
    )

    private fun recordingInterceptor(kind: String) = Interceptor { chain ->
        interceptedPaths += "$kind ${chain.request().url.encodedPath}"
        chain.proceed(chain.request())
    }

    private object FixedUserAgentProvider : UserAgentProvider {
        override fun provide() = A_USER_AGENT
    }
}

private const val A_PHONE = "+15555550101"
private const val A_USER_AGENT = "GuaTest/1.0"
