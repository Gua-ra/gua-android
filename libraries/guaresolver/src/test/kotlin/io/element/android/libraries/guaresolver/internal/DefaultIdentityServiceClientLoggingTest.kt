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
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class DefaultIdentityServiceClientLoggingTest {
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
    fun `a refusal logs its status and code but nothing else from the body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{ "code": "new_server_code", "message": "fake_detail" }"""))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{ "code": "+15550001234" }"""))
        server.enqueue(MockResponse().setResponseCode(503))
        val client = createClient()

        repeat(3) { client.cancelAccountRecovery("secret-token") }

        assertThat(logged).containsExactly(
            "Identity-service call refused with HTTP 400, code new_server_code",
            "Identity-service call refused with HTTP 400, code <unrecognized>",
            "Identity-service call refused with HTTP 503, code none",
        ).inOrder()
    }

    private fun createClient() = DefaultIdentityServiceClient(
        retrofitFactory = RetrofitFactory(
            okHttpClient = { OkHttpClient.Builder().build() },
            json = { DefaultJsonProvider() },
        ),
        enrollmentRedirectProvider = FakeEnrollmentRedirectProvider(),
        deployment = FakeGuaDeployment(identityServiceBaseUrl = server.url("/").toString()),
    )
}
