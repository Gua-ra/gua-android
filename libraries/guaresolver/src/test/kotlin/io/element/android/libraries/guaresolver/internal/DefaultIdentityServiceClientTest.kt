/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.guaresolver.AuthFactor
import io.element.android.libraries.guaresolver.FakeGuaDeployment
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class DefaultIdentityServiceClientTest {
    @Test
    fun `matches map to ContactMatch with homeserver-abstracted handle`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "matches": [
                    { "hashedPhone": "aaa", "userId": "@alice:gua.global", "username": "alice", "displayName": "Alice", "avatarUrl": "mxc://x/y" },
                    { "hashedPhone": "bbb", "userId": "@bob:matrix.gua.global" }
                  ]
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val matches = client.lookupContacts("token", listOf("aaa", "bbb")).getOrThrow()

        assertThat(matches).hasSize(2)
        assertThat(matches[0].displayHandle).isEqualTo("@alice")
        assertThat(matches[0].displayName).isEqualTo("Alice")
        assertThat(matches[0].avatarUrl).isEqualTo("mxc://x/y")
        // No username assigned -> strip the :homeserver suffix from the Matrix id.
        assertThat(matches[1].displayHandle).isEqualTo("@bob")
        server.shutdown()
    }

    @Test
    fun `request carries hashed phones and a bearer token but no raw numbers`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "matches": [] }"""))
        val client = createClient(server)

        client.lookupContacts("secret-token", listOf("hash1", "hash2")).getOrThrow()

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/directory/lookup")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        val body = request.body.readUtf8()
        assertThat(body).contains("hash1")
        assertThat(body).contains("hash2")
        // Privacy: only the hashed key is sent.
        assertThat(body).doesNotContain("+")
        server.shutdown()
    }

    @Test
    fun `empty input short-circuits without a network call`() = runTest {
        val client = DefaultIdentityServiceClient(
            retrofitFactory = retrofitFactory(),
            deployment = FakeGuaDeployment(identityServiceBaseUrl = null),
        )

        val result = client.lookupContacts("token", emptyList())

        assertThat(result.getOrThrow()).isEmpty()
    }

    @Test
    fun `server error is surfaced with the status code`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        val client = createClient(server)

        val result = client.lookupContacts("token", listOf("aaa"))

        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(ResolverError.Server::class.java)
        assertThat((error as ResolverError.Server).status).isEqualTo(503)
        server.shutdown()
    }

    @Test
    fun `unconfigured identity-service returns NotConfigured`() = runTest {
        val client = DefaultIdentityServiceClient(
            retrofitFactory = retrofitFactory(),
            deployment = FakeGuaDeployment(identityServiceBaseUrl = null),
        )

        val result = client.lookupContacts("token", listOf("aaa"))

        assertThat(result.exceptionOrNull()).isInstanceOf(ResolverError.NotConfigured::class.java)
    }

    @Test
    fun `startPasskeyEnrollment POSTs the start endpoint with a bearer token and parses enrollUrl`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{ "enrollUrl": "https://idp.gua.global/passkey/enroll?token=abc" }""")
        )
        val client = createClient(server)

        val enrollUrl = client.startPasskeyEnrollment("secret-token").getOrThrow()

        assertThat(enrollUrl).isEqualTo("https://idp.gua.global/passkey/enroll?token=abc")
        val request = server.takeRequest()
        // POST is the contract the identity service actually serves; asserting GET here is what
        // let the 405 ship.
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/security/passkey/enroll/start")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        server.shutdown()
    }

    // GUA FORK: the account factor signal and the real phone-change contract.

    @Test
    fun `accountFactorStatus parses the factors the account holds`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "hasPin": false,
                  "changePhoneCooldownRemainingSeconds": 0,
                  "passkeyRegistered": true,
                  "preferredFactor": "PASSKEY",
                  "phoneChangeStepUpFactors": ["PASSKEY", "PIN"]
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val status = client.accountFactorStatus("secret-token", "@alice:gua.global").getOrThrow()

        assertThat(status.hasPin).isFalse()
        assertThat(status.passkeyRegistered).isTrue()
        assertThat(status.preferredFactor).isEqualTo(AuthFactor.PASSKEY)
        // The account holds a passkey, so it can settle a step-up even with no PIN at all.
        assertThat(status.phoneChangeStepUpOptions).containsExactly(AuthFactor.PASSKEY)
        assertThat(status.hasStrongFactor).isTrue()
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/security/pin/status")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        server.shutdown()
    }

    @Test
    fun `accountFactorStatus derives the factors an older identity-service does not report`() = runTest {
        val server = MockWebServer()
        // The shape from before the factor policy: a PIN flag and a cooldown, nothing else.
        server.enqueue(MockResponse().setBody("""{ "hasPin": true, "changePhoneCooldownRemainingSeconds": 42 }"""))
        val client = createClient(server)

        val status = client.accountFactorStatus("token", "@alice:gua.global").getOrThrow()

        assertThat(status.passkeyRegistered).isFalse()
        assertThat(status.preferredFactor).isEqualTo(AuthFactor.PIN)
        // Derived, not defaulted to empty: an empty list would hard-block a change the old server
        // would have allowed.
        assertThat(status.phoneChangeStepUpOptions).containsExactly(AuthFactor.PIN)
        assertThat(status.changePhoneCooldownRemainingSeconds).isEqualTo(42)
        server.shutdown()
    }

    @Test
    fun `accountFactorStatus reports a live account recovery with its times`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "hasPin": true,
                  "passkeyRegistered": false,
                  "accountRecoveryPending": true,
                  "accountRecoveryCompletableAtEpochSeconds": 1790000000,
                  "accountRecoveryExpiresAtEpochSeconds": 1790604800
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val status = client.accountFactorStatus("token", "@alice:gua.global").getOrThrow()

        assertThat(status.accountRecoveryPending).isTrue()
        assertThat(status.accountRecoveryCompletableAtEpochSeconds).isEqualTo(1_790_000_000L)
        assertThat(status.accountRecoveryExpiresAtEpochSeconds).isEqualTo(1_790_604_800L)
        server.shutdown()
    }

    @Test
    fun `accountFactorStatus from an identity-service without delayed recovery reports none live`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "hasPin": true, "changePhoneCooldownRemainingSeconds": 0 }"""))
        val client = createClient(server)

        val status = client.accountFactorStatus("token", "@alice:gua.global").getOrThrow()

        assertThat(status.accountRecoveryPending).isFalse()
        assertThat(status.accountRecoveryCompletableAtEpochSeconds).isNull()
        assertThat(status.accountRecoveryExpiresAtEpochSeconds).isNull()
        server.shutdown()
    }

    @Test
    fun `recovery times are dropped when no recovery is live`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "hasPin": true,
                  "accountRecoveryPending": false,
                  "accountRecoveryCompletableAtEpochSeconds": 1790000000,
                  "accountRecoveryExpiresAtEpochSeconds": 1790604800
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val status = client.accountFactorStatus("token", "@alice:gua.global").getOrThrow()

        assertThat(status.accountRecoveryCompletableAtEpochSeconds).isNull()
        assertThat(status.accountRecoveryExpiresAtEpochSeconds).isNull()
        server.shutdown()
    }

    @Test
    fun `cancelAccountRecovery POSTs the cancel endpoint with a bearer token and accepts 204`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        val client = createClient(server)

        val result = client.cancelAccountRecovery("secret-token")

        assertThat(result.isSuccess).isTrue()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/security/recovery/cancel")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        server.shutdown()
    }

    @Test
    fun `a refused cancel surfaces as a failure`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{ "code": "unauthorized" }"""))
        val client = createClient(server)

        val result = client.cancelAccountRecovery("expired-token")

        assertThat(result.exceptionOrNull()).isEqualTo(ResolverError.Server(401))
        server.shutdown()
    }

    @Test
    fun `the reauth OTP goes to the account endpoint and the token is scoped to the phone change`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setBody("""{ "reauthToken": "tok-1", "expiresInSeconds": 300 }"""))
        val client = createClient(server)

        client.startPhoneChangeReauth("secret-token", language = "pt-BR").getOrThrow()
        val token = client.verifyPhoneChangeReauth("secret-token", code = "123456").getOrThrow()

        assertThat(token).isEqualTo("tok-1")
        val startRequest = server.takeRequest()
        assertThat(startRequest.method).isEqualTo("POST")
        assertThat(startRequest.path).isEqualTo("/account/reauth/start")
        assertThat(startRequest.getHeader("Accept-Language")).isEqualTo("pt-BR")
        val verifyRequest = server.takeRequest()
        assertThat(verifyRequest.path).isEqualTo("/account/reauth/verify")
        val body = verifyRequest.body.readUtf8()
        assertThat(body).contains("\"code\":\"123456\"")
        // Never left to the server default: a token scoped elsewhere cannot be spent here.
        assertThat(body).contains("\"operation\":\"PHONE_CHANGE\"")
        server.shutdown()
    }

    @Test
    fun `startPhoneChange posts the token, the new number and the step-up factor`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "challengeId": "ch-1", "otpExpiresInSeconds": 300 }"""))
        val client = createClient(server)

        val challenge = client.startPhoneChange(
            accessToken = "secret-token",
            reauthToken = "tok-1",
            newPhone = "+15551234567",
            pin = "246813",
            passkeyStepUpId = null,
            passkeyCredentialJson = null,
            language = "en-CA",
        ).getOrThrow()

        assertThat(challenge.challengeId).isEqualTo("ch-1")
        assertThat(challenge.otpExpiresInSeconds).isEqualTo(300)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/account/phone/change/start")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"reauthToken\":\"tok-1\"")
        assertThat(body).contains("\"newPhone\":\"+15551234567\"")
        assertThat(body).contains("\"pin\":\"246813\"")
        server.shutdown()
    }

    @Test
    fun `a passkey assertion is sent verbatim under the step-up ceremony it came from`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "challengeId": "ch-2" }"""))
        val client = createClient(server)

        client.startPhoneChange(
            accessToken = "secret-token",
            reauthToken = "tok-1",
            newPhone = "+15551234567",
            pin = null,
            passkeyStepUpId = "stepup-1",
            passkeyCredentialJson = """{"id":"cred","type":"public-key"}""",
            language = null,
        ).getOrThrow()

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"passkeyStepUpId\":\"stepup-1\"")
        // An object, not a re-encoded string: the server verifies the assertion it was handed.
        assertThat(body).contains("\"passkeyCredential\":{\"id\":\"cred\",\"type\":\"public-key\"}")
        assertThat(body).doesNotContain("\"pin\"")
        server.shutdown()
    }

    @Test
    fun `step_up_required is its own error, not one of the retryable PIN failures`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{ "code": "step_up_required", "message": "set up 2SV" }""")
        )
        val client = createClient(server)

        val error = client.startPhoneChange(
            accessToken = "token",
            reauthToken = "tok-1",
            newPhone = "+15551234567",
            pin = null,
            passkeyStepUpId = null,
            passkeyCredentialJson = null,
            language = null,
        ).exceptionOrNull()

        assertThat(error).isEqualTo(ResolverError.StepUpRequired)
        server.shutdown()
    }

    @Test
    fun `the fresh-2FA hold carries its remaining seconds out of the body`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setHeader("Retry-After", "600")
                .setBody("""{ "code": "twofa_cooldown_active", "retryAfterSeconds": 604800 }""")
        )
        val client = createClient(server)

        val error = client.startPhoneChange(
            accessToken = "token",
            reauthToken = "tok-1",
            newPhone = "+15551234567",
            pin = "246813",
            passkeyStepUpId = null,
            passkeyCredentialJson = null,
            language = null,
        ).exceptionOrNull()

        // The body wins over the header: they are the same number, but only the body is authoritative.
        assertThat(error).isEqualTo(ResolverError.TwoFactorCooldown(retryAfterSeconds = 604_800))
        server.shutdown()
    }

    @Test
    fun `the per-account phone-change cooldown is not the same refusal as the fresh-2FA hold`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(425)
                .setHeader("Retry-After", "3600")
                .setBody("""{ "code": "phone_change_cooldown", "message": "too soon" }""")
        )
        val client = createClient(server)

        val error = client.startPhoneChange(
            accessToken = "token",
            reauthToken = "tok-1",
            newPhone = "+15551234567",
            pin = "246813",
            passkeyStepUpId = null,
            passkeyCredentialJson = null,
            language = null,
        ).exceptionOrNull()

        assertThat(error).isEqualTo(ResolverError.PhoneChangeCooldown(retryAfterSeconds = 3600))
        server.shutdown()
    }

    @Test
    fun `completePhoneChange redeems the challenge with the new-number OTP`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        val client = createClient(server)

        client.completePhoneChange("secret-token", challengeId = "ch-1", code = "654321").getOrThrow()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/account/phone/change/complete")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"challengeId\":\"ch-1\"")
        assertThat(body).contains("\"code\":\"654321\"")
        server.shutdown()
    }

    @Test
    fun `a dead phone-change challenge is distinct from a dead PIN-change challenge`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{ "code": "phone_change_challenge_invalid" }""")
        )
        val client = createClient(server)

        val error = client.completePhoneChange("token", challengeId = "ch-1", code = "654321").exceptionOrNull()

        assertThat(error).isEqualTo(ResolverError.PhoneChangeChallengeInvalid)
        server.shutdown()
    }

    // GUA FORK: account genesis registration (ADM-008 Phase 3).

    @Test
    fun `registerAccountGenesis POSTs the genesis and proof and parses the accountId and handle`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {
                  "accountId": "ga1aea6aqb5opmzmutench3ggzepkhgwmkajb3epqqrhckkf7bcbcwl2cy",
                  "attachHandle": "Zm9vYmFyYmF6cXV1eGNvcmdlZ3JhdWx0",
                  "expiresAt": "2026-09-11T12:00:00Z"
                }
                """.trimIndent()
            )
        )
        val client = createClient(server)

        val registration = client.registerAccountGenesis(genesisB64Url = "R1VBRw", proofB64Url = "c2ln").getOrThrow()

        assertThat(registration.accountId).isEqualTo("ga1aea6aqb5opmzmutench3ggzepkhgwmkajb3epqqrhckkf7bcbcwl2cy")
        assertThat(registration.attachHandle).isEqualTo("Zm9vYmFyYmF6cXV1eGNvcmdlZ3JhdWx0")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/account/genesis")
        // Self-authenticating: the proof inside the body is the credential, so no bearer token is sent.
        assertThat(request.getHeader("Authorization")).isNull()
        val body = request.body.readUtf8()
        assertThat(body).contains("\"genesis\":\"R1VBRw\"")
        assertThat(body).contains("\"proof\":\"c2ln\"")
        server.shutdown()
    }

    @Test
    fun `a 503 surfaces as a server error, which is how a deployment says it does no genesis`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{ "code": "genesis_disabled" }"""))
        val client = createClient(server)

        val error = client.registerAccountGenesis("R1VBRw", "c2ln").exceptionOrNull()

        assertThat(error).isEqualTo(ResolverError.Server(503))
        server.shutdown()
    }

    @Test
    fun `a 403 surfaces as a server error, which is how a deployment declines to issue`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{ "code": "genesis_issuance_not_permitted" }"""))
        val client = createClient(server)

        val error = client.registerAccountGenesis("R1VBRw", "c2ln").exceptionOrNull()

        assertThat(error).isEqualTo(ResolverError.Server(403))
        server.shutdown()
    }

    @Test
    fun `an unconfigured deployment never reaches the network`() = runTest {
        val client = DefaultIdentityServiceClient(
            retrofitFactory = retrofitFactory(),
            deployment = FakeGuaDeployment(identityServiceBaseUrl = null),
        )

        val error = client.registerAccountGenesis("R1VBRw", "c2ln").exceptionOrNull()

        assertThat(error).isInstanceOf(ResolverError.NotConfigured::class.java)
    }

    private fun createClient(server: MockWebServer) = DefaultIdentityServiceClient(
        retrofitFactory = retrofitFactory(),
        deployment = FakeGuaDeployment(identityServiceBaseUrl = server.url("/").toString()),
    )

    private fun retrofitFactory() = RetrofitFactory(
        okHttpClient = { OkHttpClient.Builder().build() },
        json = { DefaultJsonProvider() },
    )
}
