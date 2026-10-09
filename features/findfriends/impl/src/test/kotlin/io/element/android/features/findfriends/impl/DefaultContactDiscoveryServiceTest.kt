/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.AccountGenesisRegistration
import io.element.android.libraries.guaresolver.ContactMatch
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.PhoneChangeChallenge
import io.element.android.libraries.guaresolver.PhoneHasher
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultContactDiscoveryServiceTest {
    @Test
    fun `a token that expires mid-sweep is refreshed and the sweep carries on`() = runTest {
        // One more number than a batch holds, so the sweep makes two lookups.
        val contacts = (0..BATCH_SIZE).associate { "+1555%07d".format(it) to "Contact $it" }
        val firstBatchNumber = contacts.keys.first()
        val secondBatchNumber = contacts.keys.last()
        val lookups = mutableListOf<String>()
        var sdkToken = AN_ACCESS_TOKEN
        val service = createService(
            contacts = contacts,
            lookupContacts = { token, hashes ->
                lookups += token
                // The token expires once the first batch has gone through.
                if (lookups.size > 1 && token == AN_ACCESS_TOKEN) {
                    Result.failure(ResolverError.Server(401))
                } else {
                    Result.success(listOfNotNull(aMatch(firstBatchNumber, hashes), aMatch(secondBatchNumber, hashes)))
                }
            },
            matrixClient = FakeMatrixClient(
                sessionId = A_SESSION_ID,
                accessTokenLambda = { sdkToken },
                refreshAccessTokenLambda = {
                    sdkToken = A_REFRESHED_ACCESS_TOKEN
                    sdkToken
                },
            ),
        )

        val result = service.discover()

        assertThat(lookups).containsExactly(AN_ACCESS_TOKEN, AN_ACCESS_TOKEN, A_REFRESHED_ACCESS_TOKEN).inOrder()
        assertThat(result).isInstanceOf(ContactDiscoveryResult.Success::class.java)
        assertThat((result as ContactDiscoveryResult.Success).contacts.map { it.localName })
            .containsExactly("Contact 0", "Contact $BATCH_SIZE")
    }

    @Test
    fun `a token still refused after the refresh fails the sweep`() = runTest {
        val service = createService(
            contacts = mapOf("+15550000001" to "Contact"),
            lookupContacts = { _, _ -> Result.failure(ResolverError.Server(401)) },
            matrixClient = FakeMatrixClient(
                sessionId = A_SESSION_ID,
                accessTokenLambda = { AN_ACCESS_TOKEN },
                refreshAccessTokenLambda = { A_REFRESHED_ACCESS_TOKEN },
            ),
        )

        assertThat(service.discover()).isEqualTo(ContactDiscoveryResult.Failure)
    }

    private fun aMatch(e164: String, hashes: List<String>): ContactMatch? {
        val hash = PhoneHasher.hash(e164)?.takeIf { it in hashes } ?: return null
        return ContactMatch(
            hashedPhone = hash,
            userId = "@${e164.drop(1)}:example.org",
            displayHandle = "@${e164.drop(1)}",
            displayName = null,
            avatarUrl = null,
        )
    }

    private fun TestScope.createService(
        contacts: Map<String, String>,
        lookupContacts: (String, List<String>) -> Result<List<ContactMatch>>,
        matrixClient: FakeMatrixClient,
    ) = DefaultContactDiscoveryService(
        contactsReader = object : ContactsReader {
            override fun readContacts(): Map<String, String> = contacts
        },
        identityServiceClient = LookupOnlyIdentityServiceClient(lookupContacts),
        matrixClient = matrixClient,
        sessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = AN_ACCESS_TOKEN))),
        dispatchers = testCoroutineDispatchers(),
    )

    private class LookupOnlyIdentityServiceClient(
        private val lookupContactsResult: (String, List<String>) -> Result<List<ContactMatch>>,
    ) : IdentityServiceClient {
        override suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>> =
            lookupContactsResult(accessToken, hashedPhones)

        override suspend fun accountFactorStatus(accessToken: String, userId: String): Result<AccountFactorStatus> = lambdaError()

        override suspend fun cancelAccountRecovery(accessToken: String): Result<Unit> = lambdaError()

        override suspend fun startPinEnrollment(accessToken: String): Result<String> = lambdaError()

        override suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String> = lambdaError()

        override suspend fun completePinChange(accessToken: String, challengeId: String, otpCode: String, newPin: String): Result<Unit> =
            lambdaError()

        override suspend fun startPhoneChangeReauth(accessToken: String, phone: String, language: String?): Result<Unit> = lambdaError()

        override suspend fun verifyPhoneChangeReauth(accessToken: String, phone: String, code: String): Result<String> = lambdaError()

        override suspend fun startPhoneChange(
            accessToken: String,
            reauthToken: String,
            newPhone: String,
            pin: String?,
            passkeyStepUpId: String?,
            passkeyCredentialJson: String?,
            language: String?,
        ): Result<PhoneChangeChallenge> = lambdaError()

        override suspend fun completePhoneChange(accessToken: String, challengeId: String, code: String): Result<Unit> = lambdaError()

        override suspend fun startPasskeyEnrollment(accessToken: String): Result<String> = lambdaError()

        override suspend fun registerAccountGenesis(genesisB64Url: String, proofB64Url: String): Result<AccountGenesisRegistration> =
            lambdaError()
    }

    private companion object {
        /** The service's per-request cap. */
        const val BATCH_SIZE = 1000
        const val AN_ACCESS_TOKEN = "anAccessToken"
        const val A_REFRESHED_ACCESS_TOKEN = "aRefreshedAccessToken"
    }
}
