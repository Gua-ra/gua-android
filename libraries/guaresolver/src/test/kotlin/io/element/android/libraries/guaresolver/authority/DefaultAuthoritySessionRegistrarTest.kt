/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.cryptography.impl.AESEncryptionDecryptionService
import io.element.android.libraries.cryptography.test.SimpleSecretKeyRepository
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.guaresolver.genesis.CachingPreferenceDataStoreFactory
import io.element.android.libraries.guaresolver.genesis.DefaultAccountAuthorityKeyStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * GUA FORK: what a session start owes the chain, and what it must not do while the flag is off (ADM-009).
 *
 * The real manager over a fake client, because the interesting part is which requests a session start makes,
 * and a fake manager would only be asserting that this class called the method the test expected.
 */
class DefaultAuthoritySessionRegistrarTest {
    @Test
    fun `with the flag off a session start makes no request at all`() = runTest {
        val client = FakeAccountAuthorityClient()
        val registrar = createRegistrar(client, featureEnabled = false)

        registrar.onSessionStarted(A_SESSION_ID, A_PUSH_TOKEN, FCM, AN_APP_ID, "Pixel 9")

        // Not "registered and ignored": with the flag off a deployment sees nothing from this client, which
        // is what shipping disabled means on this side.
        assertThat(client.registerNotificationCalls).isEmpty()
        assertThat(client.candidateCalls).isEmpty()
        assertThat(client.challengeCalls).isEmpty()
    }

    @Test
    fun `a session start registers the channel and offers this device's key`() = runTest {
        val client = FakeAccountAuthorityClient(stateResult = { Result.success(aRootedChain()) })
        val registrar = createRegistrar(client)

        registrar.onSessionStarted(A_SESSION_ID, A_PUSH_TOKEN, FCM, AN_APP_ID, "Pixel 9")

        val registration = client.registerNotificationCalls.single()
        assertThat(registration.token).isEqualTo(A_PUSH_TOKEN)
        assertThat(registration.platform).isEqualTo(FCM)
        assertThat(registration.appId).isEqualTo(AN_APP_ID)
        // Unbound, because this install holds no authority key yet. Its own removal still works; what it
        // cannot do is be removed from another install without a factor.
        assertThat(registration.authorityDeviceKeyB64Url).isNull()
        // And it offers its own key, which is the other end of the candidate step: a device with account
        // access and no offered key is a device the account's other phones cannot add.
        assertThat(client.candidateCalls).hasSize(1)
    }

    @Test
    fun `an install with no push destination registers nothing and still offers its key`() = runTest {
        val client = FakeAccountAuthorityClient(stateResult = { Result.success(aRootedChain()) })
        val registrar = createRegistrar(client)

        registrar.onSessionStarted(A_SESSION_ID, pushToken = null, platform = null, appId = AN_APP_ID, "Pixel 9")

        // A row with no destination is not a channel, so none is made.
        assertThat(client.registerNotificationCalls).isEmpty()
        assertThat(client.candidateCalls).hasSize(1)
    }

    @Test
    fun `a bootstrap account has nobody to grant a candidate, so none is offered`() = runTest {
        val client = FakeAccountAuthorityClient(stateResult = { Result.success(aBootstrapChain()) })
        val registrar = createRegistrar(client)

        registrar.onSessionStarted(A_SESSION_ID, A_PUSH_TOKEN, FCM, AN_APP_ID, "Pixel 9")

        assertThat(client.registerNotificationCalls).hasSize(1)
        assertThat(client.candidateCalls).isEmpty()
    }

    private fun createRegistrar(
        client: AccountAuthorityClient,
        featureEnabled: Boolean = true,
    ): AuthoritySessionRegistrar = DefaultAuthoritySessionRegistrar(
        featureFlagService = FakeFeatureFlagService(
            initialState = mapOf(FeatureFlags.AccountAuthority.key to featureEnabled),
        ),
        sessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_SESSION_ID))),
        authorityManager = DefaultAccountAuthorityManager(
            client = client,
            keyStore = DefaultAccountAuthorityKeyStore(
                secretKeyRepository = SimpleSecretKeyRepository(),
                encryptionDecryptionService = AESEncryptionDecryptionService(),
                preferenceDataStoreFactory = CachingPreferenceDataStoreFactory(),
            ),
        ),
    )

    private companion object {
        private const val A_SESSION_ID = "@alice:gua.global"
        private const val A_PUSH_TOKEN = "a-push-token"
        private const val AN_APP_ID = "global.gua.android"
        private val FCM = SecurityNotificationRegistration.PLATFORM_FCM
    }
}
