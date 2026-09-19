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
import io.element.android.libraries.guaresolver.genesis.AccountAuthorityKeyStore
import io.element.android.libraries.guaresolver.genesis.AccountId
import io.element.android.libraries.guaresolver.genesis.CachingPreferenceDataStoreFactory
import io.element.android.libraries.guaresolver.genesis.DefaultAccountAuthorityKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * GUA FORK: the three steps every authority transition takes, and the order they take them in (ADM-009).
 *
 * The key store is the real one over a real AES service, so the signatures asserted here are signatures a
 * JDK verifier accepts under the key that was published. That is the property that matters: identity-service
 * verifies with the JDK provider, and a test that verified with the same library that signed would agree
 * with itself while every record was refused.
 */
@OptIn(ExperimentalEncodingApi::class)
class DefaultAccountAuthorityManagerTest {
    @Test
    fun `an adoption signs magic, challenge and the record, and submits all three`() = runTest {
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(client, keyStore)
        val offer = manager.beginAdoption().getOrThrow()
        // Read before submitting: a submitted adoption moves the pair out of the adoption slot, which is
        // what stops a second adoption on this device minting keys the chain has no place for.
        val keys = checkNotNull(keyStore.adoptionKeys())

        val submitted = manager.adopt(
            accessToken = A_TOKEN,
            chain = aBootstrapChain(),
            deviceLabel = "Pixel 9",
            pin = "123456",
            artifactConfirmed = true,
        ).getOrThrow()

        assertThat(offer.recoveryArtifact).startsWith(RecoveryArtifact.PREFIX)
        assertThat(submitted.seq).isEqualTo(1)
        // The step-up is spent in the same call that mints the challenge, so it can never be older than it.
        assertThat(client.challengeCalls).containsExactly(
            FakeAccountAuthorityClient.ChallengeCall(AuthorityPurpose.ADOPT, "123456")
        )
        val submission = client.adoptCalls.single()
        assertThat(submission.recoveryArtifactConfirmed).isTrue()
        // The challenge travels back because the server stores only its hash.
        assertThat(submission.challengeB64Url).isEqualTo(FakeAccountAuthorityClient.A_CHALLENGE_B64)

        val record = decode(submission.recordB64Url)
        assertThat(record).hasLength(177)
        assertThat(record.copyOfRange(6, 40))
            .isEqualTo(AccountId.parse(A_BOOTSTRAP_ACCOUNT_ID).rawBytes())
        assertThat(record.copyOfRange(80, 112)).isEqualTo(keys.deviceAuthorityPublicKey())
        assertThat(record.copyOfRange(113, 145)).isEqualTo(keys.recoveryAuthorityPublicKey())

        val preimage = AuthorityProofs.recordPreimage(
            AuthorityRecordType.ADOPT_ROOT,
            decode(FakeAccountAuthorityClient.A_CHALLENGE_B64),
            record,
        )
        assertThat(verify(keys.deviceAuthorityPublicKey(), preimage, decode(submission.signatureB64Url))).isTrue()
        // The same signature over the record alone must not verify, which is what puts the challenge and the
        // domain inside the signature rather than beside it.
        assertThat(verify(keys.deviceAuthorityPublicKey(), record, decode(submission.signatureB64Url))).isFalse()
    }

    @Test
    fun `an adoption whose artifact was not confirmed never reaches the server`() = runTest {
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(client, keyStore)
        manager.beginAdoption().getOrThrow()

        val result = manager.adopt(
            accessToken = A_TOKEN,
            chain = aBootstrapChain(),
            deviceLabel = "Pixel 9",
            pin = "123456",
            artifactConfirmed = false,
        )

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthorityError.ArtifactUnconfirmed::class.java)
        // Not even a challenge: a refused adoption must not cost the user a step-up, and the artifact is
        // what makes adoption recoverable at all.
        assertThat(client.challengeCalls).isEmpty()
        assertThat(client.adoptCalls).isEmpty()
    }

    @Test
    fun `the adopted pair becomes this device's authority, and a second account cannot take it`() = runTest {
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(FakeAccountAuthorityClient(), keyStore)
        manager.beginAdoption().getOrThrow()
        assertThat(manager.holdsAuthority()).isFalse()

        manager.adopt(A_TOKEN, aBootstrapChain(), "Pixel 9", "123456", artifactConfirmed = true).getOrThrow()

        assertThat(manager.holdsAuthority()).isTrue()
        assertThat(keyStore.adoptedAccountId()).isEqualTo(A_BOOTSTRAP_ACCOUNT_ID)
    }

    @Test
    fun `a grant appends at the reported head and names this device as the authorizing key`() = runTest {
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(client, keyStore)
        manager.beginAdoption().getOrThrow()
        manager.adopt(A_TOKEN, aBootstrapChain(), "Pixel 9", "123456", artifactConfirmed = true).getOrThrow()
        val ourKey = checkNotNull(keyStore.authorityDevicePublicKey())

        manager.grantDevice(
            accessToken = A_TOKEN,
            chain = aRootedChain(headSeq = 3),
            granteeDeviceKeyB64Url = encode(A_GRANTEE_KEY),
            label = "Pixel Tablet",
            pin = "123456",
        ).getOrThrow()

        assertThat(client.challengeCalls.last().purpose).isEqualTo(AuthorityPurpose.GRANT)
        val record = decode(client.grantCalls.single().recordB64Url)
        assertThat(record).hasLength(161)
        assertThat(record.copyOfRange(40, 72)).isEqualTo(AuthorityRecordCodec.prevHashFromHex(A_HEAD_HASH_HEX))
        assertThat(record.copyOfRange(72, 80)).isEqualTo(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 4))
        // The new device's own key is what is granted: this client never sends one of its own.
        assertThat(record.copyOfRange(80, 112)).isEqualTo(A_GRANTEE_KEY)
        assertThat(record.copyOfRange(129, 161)).isEqualTo(ourKey)

        val preimage = AuthorityProofs.recordPreimage(
            AuthorityRecordType.DEVICE_GRANT,
            decode(FakeAccountAuthorityClient.A_CHALLENGE_B64),
            record,
        )
        assertThat(verify(ourKey, preimage, decode(client.grantCalls.single().signatureB64Url))).isTrue()
    }

    @Test
    fun `a device with no authority cannot grant one`() = runTest {
        val client = FakeAccountAuthorityClient()
        val manager = DefaultAccountAuthorityManager(client, createKeyStore())

        val result = manager.grantDevice(
            accessToken = A_TOKEN,
            chain = aRootedChain(),
            granteeDeviceKeyB64Url = encode(A_GRANTEE_KEY),
            label = "Pixel Tablet",
            pin = "123456",
        )

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthorityError.SignerRefused::class.java)
        assertThat(client.challengeCalls).isEmpty()
    }

    @Test
    fun `an approval is signed over its own challenge, with no new challenge minted`() = runTest {
        val approval = anApproval()
        val client = FakeAccountAuthorityClient(approvalsResult = { Result.success(listOf(approval)) })
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(client, keyStore)
        manager.beginAdoption().getOrThrow()
        manager.adopt(A_TOKEN, aBootstrapChain(), "Pixel 9", "123456", artifactConfirmed = true).getOrThrow()
        val ourKey = checkNotNull(keyStore.authorityDevicePublicKey())

        manager.approve(A_TOKEN, aRootedChain(), approval).getOrThrow()

        // The browser's start minted the challenge; the device signs that one rather than asking for its own.
        assertThat(client.challengeCalls.map { it.purpose }).containsExactly(AuthorityPurpose.ADOPT)
        val (approvalId, signature) = client.signApprovalCalls.single()
        assertThat(approvalId).isEqualTo(approval.approvalId)
        val preimage = AuthorityProofs.approvalPreimage(
            accountReference = AccountId.parse(A_BOOTSTRAP_ACCOUNT_ID).rawBytes(),
            approvalId = decode(approval.approvalId),
            actionDigest = decode(approval.actionDigestB64Url),
            challenge = decode(approval.challengeB64Url),
        )
        assertThat(verify(ourKey, preimage, decode(signature))).isTrue()
    }

    @Test
    fun `an account id the client cannot re-encode is refused before anything is signed`() = runTest {
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(client, keyStore)
        manager.beginAdoption().getOrThrow()

        val result = manager.adopt(
            accessToken = A_TOKEN,
            chain = aBootstrapChain(accountId = "ga1not-a-canonical-id"),
            deviceLabel = "Pixel 9",
            pin = "123456",
            artifactConfirmed = true,
        )

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthorityError.NoAccount::class.java)
        assertThat(client.challengeCalls).isEmpty()
    }

    @Test
    fun `the first opposition carries no factor and the second carries the PIN`() = runTest {
        val client = FakeAccountAuthorityClient()
        val manager = DefaultAccountAuthorityManager(client, createKeyStore())

        manager.oppose(A_TOKEN, recordHash = "a-record-hash", pin = null).getOrThrow()
        manager.oppose(A_TOKEN, recordHash = "a-record-hash", pin = "123456").getOrThrow()

        assertThat(client.opposeCalls).containsExactly(
            "a-record-hash" to null,
            "a-record-hash" to "123456",
        ).inOrder()
    }

    private fun createKeyStore(): AccountAuthorityKeyStore = DefaultAccountAuthorityKeyStore(
        secretKeyRepository = SimpleSecretKeyRepository(),
        encryptionDecryptionService = AESEncryptionDecryptionService(),
        preferenceDataStoreFactory = CachingPreferenceDataStoreFactory(),
    )

    private fun encode(value: ByteArray): String = Base64.UrlSafe.encode(value).trimEnd('=')

    private fun decode(value: String): ByteArray {
        val padding = (4 - value.length % 4) % 4
        return Base64.UrlSafe.decode(value + "=".repeat(padding))
    }

    /** Verifies with the JDK provider, which is what identity-service verifies with. */
    private fun verify(rawPublicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val spki = SPKI_PREFIX.chunked(2).map { it.toInt(16).toByte() }.toByteArray() + rawPublicKey
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        return Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(message)
            verify(signature)
        }
    }

    private companion object {
        private const val SPKI_PREFIX = "302a300506032b6570032100"
        private const val A_TOKEN = "an-access-token"

        /** A real curve point, since the codec refuses anything else. */
        private val A_GRANTEE_KEY = com.google.crypto.tink.subtle.Ed25519Sign.KeyPair.newKeyPair().publicKey
    }
}
