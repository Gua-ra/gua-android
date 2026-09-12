/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.cryptography.impl.AESEncryptionDecryptionService
import io.element.android.libraries.cryptography.test.SimpleSecretKeyRepository
import io.element.android.libraries.guaresolver.AccountGenesisRegistration
import io.element.android.libraries.guaresolver.FakeIdentityServiceClient
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.ResolverError
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * GUA FORK: the client half of ADM-008 decision 6, end to end against a recorded identity-service.
 *
 * The two branches that matter are the ones that look alike and are not: a deployment which says it does
 * not do genesis continues silently with no handle, and a client which meant to register one and could
 * not fails the signup.
 */
@OptIn(ExperimentalEncodingApi::class)
class DefaultAccountGenesisManagerTest {
    private var sentGenesis: String? = null
    private var sentProof: String? = null

    @Test
    fun `registration sends the minted genesis with a proof that verifies, and keeps the handle`() = runTest {
        val manager = createManager(capturingClient())

        val registration = manager.registerForSignup()

        assertThat(registration).isInstanceOf(GenesisRegistration.Registered::class.java)
        val registered = registration as GenesisRegistration.Registered
        assertThat(registered.attachHandle).isEqualTo(FakeIdentityServiceClient.A_FAKE_ATTACH_HANDLE)

        // What crossed the wire must decode under the server's own rules and re-derive the same id.
        val canonicalBytes = decodeBase64Url(checkNotNull(sentGenesis))
        val genesis = AccountGenesisCodec.decode(canonicalBytes)
        assertThat(genesis.accountId().value).isEqualTo(registered.accountId.value)
        assertThat(genesis.recoveryFrameworkId).isEqualTo(AccountGenesis.RECOVERY_FRAMEWORK_COMMITTED_KEY)
        assertThat(canonicalBytes).hasLength(AccountGenesis.LENGTH)

        // The registration proof is a signature by the committed authority key over the domain and bytes.
        val proof = decodeBase64Url(checkNotNull(sentProof))
        assertThat(verify(genesis.authorityPublicKey(), GenesisProofs.genesisProofPreimage(canonicalBytes), proof)).isTrue()
    }

    @Test
    fun `two signups mint distinct genesis bytes and distinct accountIds`() = runTest {
        val first = createManager(capturingClient()).registerForSignup() as GenesisRegistration.Registered
        val second = createManager(capturingClient()).registerForSignup() as GenesisRegistration.Registered

        assertThat(first.accountId.value).isNotEqualTo(second.accountId.value)
    }

    @Test
    fun `a 503 means the deployment does not do genesis, so the signup continues with no handle`() = runTest {
        val client = capturingClient(result = { Result.failure(ResolverError.Server(503)) })

        val registration = createManager(client).registerForSignup()

        assertThat(registration).isEqualTo(GenesisRegistration.Unavailable)
    }

    @Test
    fun `a 403 means issuance is not permitted here, which is also the no-handle branch`() = runTest {
        val client = capturingClient(result = { Result.failure(ResolverError.Server(403)) })

        val registration = createManager(client).registerForSignup()

        assertThat(registration).isEqualTo(GenesisRegistration.Unavailable)
    }

    @Test
    fun `any other failure fails the signup rather than downgrading it silently`() = runTest {
        val client = capturingClient(result = { Result.failure(ResolverError.Server(400)) })

        val registration = createManager(client).registerForSignup()

        assertThat(registration).isInstanceOf(GenesisRegistration.Failed::class.java)
    }

    @Test
    fun `a transport failure fails the signup`() = runTest {
        val client = capturingClient(result = { Result.failure(ResolverError.Transport(Exception("offline"))) })

        val registration = createManager(client).registerForSignup()

        assertThat(registration).isInstanceOf(GenesisRegistration.Failed::class.java)
    }

    @Test
    fun `an accountId the server did not derive from these bytes fails the signup`() = runTest {
        val client = capturingClient(
            result = {
                Result.success(
                    AccountGenesisRegistration(
                        accountId = "ga1aeatmvszaxoxcnsrkzpzbvaust6jcdhcapmia7snhqrspmukdojzkgq",
                        attachHandle = FakeIdentityServiceClient.A_FAKE_ATTACH_HANDLE,
                    )
                )
            }
        )

        val registration = createManager(client).registerForSignup()

        assertThat(registration).isInstanceOf(GenesisRegistration.Failed::class.java)
    }

    @Test
    fun `a malformed attach handle is refused rather than sent on in a login hint`() = runTest {
        val client = capturingClient(handleOverride = "not a handle")

        val registration = createManager(client).registerForSignup()

        assertThat(registration).isInstanceOf(GenesisRegistration.Failed::class.java)
    }

    @Test
    fun `the attach proof signs the fixed-length preimage and verifies under the committed key`() = runTest {
        val manager = createManager(capturingClient())
        val registered = manager.registerForSignup() as GenesisRegistration.Registered
        val challenge = ByteArray(GenesisProofs.ATTACH_CHALLENGE_LENGTH) { it.toByte() }

        val proof = manager.signAttachProof(encodeBase64Url(challenge)).getOrThrow()

        val genesis = AccountGenesisCodec.decode(decodeBase64Url(checkNotNull(sentGenesis)))
        val preimage = GenesisProofs.attachProofPreimage(challenge, registered.accountId)
        assertThat(preimage).hasLength(GenesisProofs.ATTACH_PREIMAGE_LENGTH)
        assertThat(verify(genesis.authorityPublicKey(), preimage, decodeBase64Url(proof))).isTrue()
    }

    @Test
    fun `signing an attach proof before any registration fails`() = runTest {
        val manager = createManager(capturingClient())
        val challenge = encodeBase64Url(ByteArray(GenesisProofs.ATTACH_CHALLENGE_LENGTH))

        assertThat(manager.signAttachProof(challenge).isFailure).isTrue()
    }

    @Test
    fun `a challenge that is not 32 bytes is refused`() = runTest {
        val manager = createManager(capturingClient())
        manager.registerForSignup()

        assertThat(manager.signAttachProof(encodeBase64Url(ByteArray(31))).isFailure).isTrue()
    }

    /**
     * The shared fake, wired to record what crossed the wire and, by default, to derive the accountId
     * from the bytes it received exactly as identity-service does.
     */
    private fun capturingClient(
        result: ((String) -> Result<AccountGenesisRegistration>)? = null,
        handleOverride: String? = null,
    ) = FakeIdentityServiceClient(
        registerAccountGenesisResult = { genesis, proof ->
            sentGenesis = genesis
            sentProof = proof
            result?.invoke(genesis) ?: Result.success(
                AccountGenesisRegistration(
                    accountId = AccountGenesisCodec.decode(decodeBase64Url(genesis)).accountId().value,
                    attachHandle = handleOverride ?: FakeIdentityServiceClient.A_FAKE_ATTACH_HANDLE,
                )
            )
        },
    )

    private fun createManager(client: IdentityServiceClient) = DefaultAccountGenesisManager(
        keyStore = DefaultAccountAuthorityKeyStore(
            secretKeyRepository = SimpleSecretKeyRepository(),
            encryptionDecryptionService = AESEncryptionDecryptionService(),
            preferenceDataStoreFactory = CachingPreferenceDataStoreFactory(),
        ),
        identityServiceClient = client,
    )

    private fun verify(rawPublicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val spki = "302a300506032b6570032100".chunked(2).map { it.toInt(16).toByte() }.toByteArray() + rawPublicKey
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        return Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(message)
            verify(signature)
        }
    }

    private fun encodeBase64Url(value: ByteArray): String = Base64.UrlSafe.encode(value).trimEnd('=')

    private fun decodeBase64Url(value: String): ByteArray =
        Base64.UrlSafe.decode(value + "=".repeat((4 - value.length % 4) % 4))
}
