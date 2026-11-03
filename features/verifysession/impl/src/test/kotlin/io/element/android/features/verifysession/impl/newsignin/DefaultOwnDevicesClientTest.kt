/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class DefaultOwnDevicesClientTest {
    private val server = MockWebServer()
    private val sessionStore = InMemorySessionStore(
        initialList = listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = "token-1").copy(homeserverUrl = server.url("/").toString())),
    )
    private val client = DefaultOwnDevicesClient(
        matrixClient = FakeMatrixClient(deviceId = OWN_DEVICE),
        sessionStore = sessionStore,
        retrofitFactory = RetrofitFactory(okHttpClient = { OkHttpClient() }, json = { DefaultJsonProvider() }),
    )

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `the nudge is a PUT of the retry event to the target device only`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        val result = client.sendRetryRequest(DeviceId("NEWDEVICE"))

        assertThat(result.isSuccess).isTrue()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).matches("/_matrix/client/v3/sendToDevice/global\\.gua\\.verification\\.retry_request/[^/]+")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-1")
        assertThat(request.body.readUtf8()).isEqualTo(
            """{"messages":{"${A_SESSION_ID.value}":{"NEWDEVICE":{"from_device":"${OWN_DEVICE.value}"}}}}"""
        )
    }

    @Test
    fun `every nudge has its own transaction id`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))

        client.sendRetryRequest(DeviceId("NEWDEVICE"))
        client.sendRetryRequest(DeviceId("NEWDEVICE"))

        assertThat(server.takeRequest().path).isNotEqualTo(server.takeRequest().path)
    }

    @Test
    fun `devices are read with their last seen time`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"devices":[{"device_id":"NEWDEVICE","display_name":"Web","last_seen_ip":"1.2.3.4","last_seen_ts":1800000000000},{"device_id":"OLD"}]}"""
            )
        )

        val devices = client.devices().getOrThrow()

        assertThat(devices).containsExactly(OwnDevice(DeviceId("NEWDEVICE"), 1_800_000_000_000), OwnDevice(DeviceId("OLD"), null))
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/_matrix/client/v3/devices")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-1")
    }

    @Test
    fun `the keys query asks for every device of this account and reads signatures and dehydration`() = runTest {
        val me = A_SESSION_ID.value
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "device_keys": {
                    "$me": {
                      "OWNDEVICE": {"signatures": {"$me": {"ed25519:OWNDEVICE": "s1", "ed25519:ssk-key": "s2"}}},
                      "NEWDEVICE": {"signatures": {"$me": {"ed25519:NEWDEVICE": "s3"}}},
                      "DEHYDRATED": {"dehydrated": true, "signatures": {"$me": {"ed25519:ssk-key": "s4"}}}
                    },
                    "@someone:else.org": {"THEIRS": {"signatures": {}}}
                  },
                  "self_signing_keys": {"$me": {"user_id": "$me", "usage": ["self_signing"], "keys": {"ed25519:ssk-key": "ssk-key"}}}
                }
                """.trimIndent()
            )
        )

        val keys = client.deviceKeys().getOrThrow()

        assertThat(keys.selfSigningKeyIds).containsExactly("ed25519:ssk-key")
        assertThat(keys.devices).containsExactlyEntriesIn(
            mapOf(
                DeviceId("OWNDEVICE") to OwnDeviceKeyInfo(ownSignatureKeyIds = setOf("ed25519:OWNDEVICE", "ed25519:ssk-key"), isDehydrated = false),
                DeviceId("NEWDEVICE") to OwnDeviceKeyInfo(ownSignatureKeyIds = setOf("ed25519:NEWDEVICE"), isDehydrated = false),
                DeviceId("DEHYDRATED") to OwnDeviceKeyInfo(ownSignatureKeyIds = setOf("ed25519:ssk-key"), isDehydrated = true),
            )
        )
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/_matrix/client/v3/keys/query")
        assertThat(request.body.readUtf8()).isEqualTo("""{"device_keys":{"$me":[]}}""")
    }

    @Test
    fun `an account without cross-signing keys has no self-signing key id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"device_keys": {}}"""))

        val keys = client.deviceKeys().getOrThrow()

        assertThat(keys.selfSigningKeyIds).isEmpty()
        assertThat(keys.devices).isEmpty()
    }

    @Test
    fun `a 401 is a failed result carrying the status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"errcode":"M_UNKNOWN_TOKEN","error":"Token expired","soft_logout":true}"""))

        val result = client.devices()

        assertThat((result.exceptionOrNull() as OwnDevicesRequestException).httpCode).isEqualTo(401)
    }

    @Test
    fun `each call reads the current access token`() = runTest {
        server.enqueue(MockResponse().setBody("""{"devices":[]}"""))
        server.enqueue(MockResponse().setBody("""{"devices":[]}"""))

        client.devices()
        sessionStore.updateData(sessionStore.getSession(A_SESSION_ID.value)!!.copy(accessToken = "token-2"))
        client.devices()

        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer token-1")
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer token-2")
    }

    private companion object {
        val OWN_DEVICE = DeviceId("OWNDEVICE")
    }
}
