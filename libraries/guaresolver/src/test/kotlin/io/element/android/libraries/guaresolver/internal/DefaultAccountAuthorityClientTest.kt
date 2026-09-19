/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.guaresolver.FakeGuaDeployment
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityPurpose
import io.element.android.libraries.guaresolver.authority.AuthorityRecordSubmission
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

/**
 * GUA FORK: the wire the two halves of ADM-009 have to agree on.
 *
 * The assertions about what is absent matter as much as the ones about what is present: there is no OTP
 * field on any request in this feature, in any combination, at any step (decision 9).
 */
class DefaultAccountAuthorityClientTest {
    @Test
    fun `a challenge request carries the purpose and the step-up, and never a phone code`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "challenge": "Y2hhbGxlbmdl", "expiresInSeconds": 900 }"""))
        val client = createClient(server)

        val challenge = client.challenge("a-token", AuthorityPurpose.ADOPT, pin = "123456").getOrThrow()

        assertThat(challenge.challengeB64Url).isEqualTo("Y2hhbGxlbmdl")
        assertThat(challenge.expiresInSeconds).isEqualTo(900)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/account/authority/challenge")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer a-token")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"purpose\":\"ADOPT\"")
        assertThat(body).contains("\"pin\":\"123456\"")
        assertThat(body).doesNotContain("otp")
        assertThat(body).doesNotContain("code")
        server.shutdown()
    }

    @Test
    fun `an adoption submits the record, the signature and the challenge it signed`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(202).setBody(
                """{ "seq": 1, "state": "PENDING", "effectiveAtEpochSeconds": 1800000000, "recordHash": "abcd" }"""
            )
        )
        val client = createClient(server)

        val submitted = client.adopt(
            accessToken = "a-token",
            submission = AuthorityRecordSubmission(
                recordB64Url = "cmVjb3Jk",
                signatureB64Url = "c2ln",
                challengeB64Url = "Y2hhbGxlbmdl",
                recoveryArtifactConfirmed = true,
            ),
        ).getOrThrow()

        assertThat(submitted.seq).isEqualTo(1)
        assertThat(submitted.state).isEqualTo("PENDING")
        assertThat(submitted.recordHash).isEqualTo("abcd")
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"record\":\"cmVjb3Jk\"")
        assertThat(body).contains("\"signature\":\"c2ln\"")
        // The challenge travels back because only its SHA-256 is stored server side.
        assertThat(body).contains("\"challenge\":\"Y2hhbGxlbmdl\"")
        assertThat(body).contains("\"recoveryArtifactConfirmed\":true")
        server.shutdown()
    }

    @Test
    fun `the chain, its device set and its pending transition are read as the chain states them`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "accountId": "ga1abc",
                  "accountClass": "BOOTSTRAP",
                  "state": "ROOTED",
                  "headSeq": 2,
                  "headHash": "ff00",
                  "devices": [
                    { "deviceKey": "a-key", "label": "Pixel 9", "state": "ACTIVE", "grantedSeq": 1 },
                    { "deviceKey": "b-key", "label": "Tablet", "state": "QUARANTINED",
                      "quarantineUntilEpochSeconds": 1800000000, "grantedSeq": 2 }
                  ],
                  "pending": { "type": "DEVICE_REVOKE", "seq": 3, "effectiveAtEpochSeconds": 1800000900,
                               "recordHash": "beef" }
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val state = client.state("a-token").getOrThrow()

        assertThat(state.accountId).isEqualTo("ga1abc")
        assertThat(state.state).isEqualTo("ROOTED")
        assertThat(state.headSeq).isEqualTo(2)
        assertThat(state.devices.first().isActive).isTrue()
        // A quarantined device counts for nothing and can do nothing, so it is never read as active.
        assertThat(state.devices[1].isQuarantined).isTrue()
        assertThat(state.devices[1].isActive).isFalse()
        assertThat(state.devices[1].quarantineUntilEpochSeconds).isEqualTo(1_800_000_000)
        assertThat(state.pending?.type).isEqualTo("DEVICE_REVOKE")
        assertThat(state.canAdopt).isFalse()
        assertThat(server.takeRequest().path).isEqualTo("/account/authority")
        server.shutdown()
    }

    @Test
    fun `a deployment with the feature off reads as disabled rather than as an error`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{ "code": "authority_disabled", "message": "off" }""")
        )
        val client = createClient(server)

        val error = client.state("a-token").exceptionOrNull()

        assertThat(error).isInstanceOf(AuthorityError.Disabled::class.java)
        server.shutdown()
    }

    @Test
    fun `a 503 with no code at all is still the feature being off`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        val client = createClient(server)

        val error = client.state("a-token").exceptionOrNull()

        // Every deployment is in this state today, and showing an error for the normal case would be wrong.
        assertThat(error).isInstanceOf(AuthorityError.Disabled::class.java)
        server.shutdown()
    }

    @Test
    fun `each refusal keeps the rule that refused it`() = runTest {
        assertThat(errorFor(409, "authority_step_up_required")).isInstanceOf(AuthorityError.StepUpRequired::class.java)
        assertThat(errorFor(403, "authority_factor_too_fresh")).isInstanceOf(AuthorityError.FactorTooFresh::class.java)
        assertThat(errorFor(403, "authority_recovery_too_recent"))
            .isInstanceOf(AuthorityError.RecoveryTooRecent::class.java)
        assertThat(errorFor(403, "authority_artifact_unconfirmed"))
            .isInstanceOf(AuthorityError.ArtifactUnconfirmed::class.java)
        assertThat(errorFor(409, "authority_position_refused"))
            .isInstanceOf(AuthorityError.PositionRefused::class.java)
        assertThat(errorFor(409, "authority_last_device")).isInstanceOf(AuthorityError.LastDevice::class.java)
        assertThat(errorFor(403, "authority_device_quarantined"))
            .isInstanceOf(AuthorityError.DeviceQuarantined::class.java)
        assertThat(errorFor(400, "invalid_authority_record")).isInstanceOf(AuthorityError.InvalidRecord::class.java)
    }

    @Test
    fun `a backoff keeps the seconds to wait`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("Retry-After", "600")
                .setBody("""{ "code": "authority_backoff", "message": "wait" }""")
        )
        val client = createClient(server)

        val error = client.state("a-token").exceptionOrNull()

        assertThat(error).isInstanceOf(AuthorityError.Backoff::class.java)
        assertThat((error as AuthorityError.Backoff).retryAfterSeconds).isEqualTo(600)
        server.shutdown()
    }

    @Test
    fun `an unconfigured deployment is refused without a request`() = runTest {
        val client = DefaultAccountAuthorityClient(
            retrofitFactory = retrofitFactory(),
            deployment = FakeGuaDeployment(identityServiceBaseUrl = null),
        )

        assertThat(client.state("a-token").exceptionOrNull())
            .isInstanceOf(AuthorityError.NotConfigured::class.java)
    }

    private suspend fun errorFor(status: Int, code: String): Throwable? {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(status).setBody("""{ "code": "$code", "message": "refused" }""")
        )
        val client = createClient(server)
        val error = client.state("a-token").exceptionOrNull()
        server.shutdown()
        return error
    }

    private fun createClient(server: MockWebServer) = DefaultAccountAuthorityClient(
        retrofitFactory = retrofitFactory(),
        deployment = FakeGuaDeployment(identityServiceBaseUrl = server.url("/").toString()),
    )

    private fun retrofitFactory() = RetrofitFactory(
        okHttpClient = { OkHttpClient.Builder().build() },
        json = { DefaultJsonProvider() },
    )
}
