/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Ed25519Sign
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
            stepUp = A_PIN,
            artifactConfirmed = true,
        ).getOrThrow()

        assertThat(offer.recoveryArtifact).startsWith(RecoveryArtifact.PREFIX)
        assertThat(submitted.seq).isEqualTo(1)
        // The step-up is spent in the same call that mints the challenge, so it can never be older than it.
        assertThat(client.challengeCalls).containsExactly(
            FakeAccountAuthorityClient.ChallengeCall(AuthorityPurpose.ADOPT, A_PIN)
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
            stepUp = A_PIN,
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

        manager.adopt(A_TOKEN, aBootstrapChain(), "Pixel 9", A_PIN, artifactConfirmed = true).getOrThrow()

        assertThat(manager.holdsAuthority()).isTrue()
        assertThat(keyStore.adoptedAccountId()).isEqualTo(A_BOOTSTRAP_ACCOUNT_ID)
    }

    @Test
    fun `a grant appends at the reported head and names this device as the authorizing key`() = runTest {
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(client, keyStore)
        manager.beginAdoption().getOrThrow()
        manager.adopt(A_TOKEN, aBootstrapChain(), "Pixel 9", A_PIN, artifactConfirmed = true).getOrThrow()
        val ourKey = checkNotNull(keyStore.authorityDevicePublicKey())

        manager.grantDevice(
            accessToken = A_TOKEN,
            chain = aRootedChain(headSeq = 3),
            candidate = aCandidate(),
            stepUp = A_PIN,
            fingerprintConfirmed = true,
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
            candidate = aCandidate(),
            stepUp = A_PIN,
            fingerprintConfirmed = true,
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
        manager.adopt(A_TOKEN, aBootstrapChain(), "Pixel 9", A_PIN, artifactConfirmed = true).getOrThrow()
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
            stepUp = A_PIN,
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

    @Test
    fun `a grant nobody compared is refused before a challenge is spent`() = runTest {
        val client = FakeAccountAuthorityClient()
        val manager = adoptedManager(client)

        val result = manager.grantDevice(
            accessToken = A_TOKEN,
            chain = aRootedChain(),
            candidate = aCandidate(),
            stepUp = A_PIN,
            fingerprintConfirmed = false,
        )

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthorityError.SignerRefused::class.java)
        assertThat(client.grantCalls).isEmpty()
        assertThat(client.challengeCalls.map { it.purpose }).containsExactly(AuthorityPurpose.ADOPT)
    }

    @Test
    fun `a fingerprint that is not the one those bytes produce is refused`() = runTest {
        val client = FakeAccountAuthorityClient()
        val manager = adoptedManager(client)

        // The person confirmed eight characters. If the key does not produce them, they compared something
        // else, and the only value the comparison had was that both phones derived it from the same key.
        val result = manager.grantDevice(
            accessToken = A_TOKEN,
            chain = aRootedChain(),
            candidate = aCandidate(fingerprint = "AAAAAAAA"),
            stepUp = A_PIN,
            fingerprintConfirmed = true,
        )

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthorityError.UnknownCandidate::class.java)
        assertThat(client.grantCalls).isEmpty()
    }

    @Test
    fun `this device offers its own key and never one it was given`() = runTest {
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(client, keyStore)

        val candidate = manager.offerThisDeviceForGrant(A_TOKEN, "Pixel 9").getOrThrow()

        val offered = checkNotNull(keyStore.candidateDevicePublicKey())
        assertThat(client.candidateCalls.single().first).isEqualTo(encode(offered))
        // Recomputed from the key, so the eight characters the other phone shows are the ones these bytes
        // produce rather than the ones the response claimed.
        assertThat(candidate.fingerprint).isEqualTo(AuthorityFingerprint.of(offered))
        // Offering it is not holding authority: the chain has not granted anything yet.
        assertThat(manager.holdsAuthority()).isFalse()
    }

    @Test
    fun `a revocation appends at the head and names the key that signs it`() = runTest {
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = adoptedManager(client, keyStore)
        val ourKey = checkNotNull(keyStore.authorityDevicePublicKey())

        manager.revokeDevice(
            accessToken = A_TOKEN,
            chain = aRootedChain(headSeq = 2),
            deviceKeyB64Url = encode(A_GRANTEE_KEY),
            reason = AuthorityRecord.REASON_COMPROMISED,
            stepUp = A_PIN,
        ).getOrThrow()

        assertThat(client.challengeCalls.last().purpose).isEqualTo(AuthorityPurpose.REVOKE)
        val record = decode(client.revokeCalls.single().recordB64Url)
        assertThat(record).hasLength(145)
        assertThat(record.copyOfRange(72, 80)).isEqualTo(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 3))
        assertThat(record.copyOfRange(80, 112)).isEqualTo(A_GRANTEE_KEY)
        assertThat(record[112].toInt()).isEqualTo(AuthorityRecord.REASON_COMPROMISED)
        assertThat(record.copyOfRange(113, 145)).isEqualTo(ourKey)
    }

    @Test
    fun `an objection is a signed record that asks for no factor and carries the pending position`() = runTest {
        val pending = AuthorityPendingTransition(
            type = "DEVICE_REVOKE",
            seq = 4,
            effectiveAtEpochSeconds = 1_800_000_000,
            recordHash = A_PENDING_RECORD_HASH,
        )
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = adoptedManager(client, keyStore)
        val ourKey = checkNotNull(keyStore.authorityDevicePublicKey())

        manager.opposeWithRecord(A_TOKEN, aRootedChain(headSeq = 3, pending = pending)).getOrThrow()

        val call = client.challengeCalls.last()
        assertThat(call.purpose).isEqualTo(AuthorityPurpose.OPPOSE)
        // No factor, because the hold gates starting a transition and never objecting to one: an owner who
        // just changed their PIN to lock a thief out must not be the one disarmed by it.
        assertThat(call.stepUp).isEqualTo(AuthorityStepUp.None)
        val record = decode(client.opposeRecordCalls.single().recordB64Url)
        assertThat(record).hasLength(144)
        assertThat(record.copyOfRange(0, 4)).isEqualTo("GUAO".toByteArray())
        // The position of the record it cancels, not a place of its own.
        assertThat(record.copyOfRange(40, 72)).isEqualTo(AuthorityRecordCodec.prevHashFromHex(A_HEAD_HASH_HEX))
        assertThat(record.copyOfRange(72, 80)).isEqualTo(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 4))
        assertThat(record.copyOfRange(80, 112))
            .isEqualTo(AuthorityRecordCodec.prevHashFromHex(A_PENDING_RECORD_HASH))
        assertThat(record.copyOfRange(112, 144)).isEqualTo(ourKey)
        // The session route is untouched: it is the objection to an adoption and nothing else.
        assertThat(client.opposeCalls).isEmpty()
    }

    @Test
    fun `a device with no authority key cannot sign an objection`() = runTest {
        val client = FakeAccountAuthorityClient()
        val manager = DefaultAccountAuthorityManager(client, createKeyStore())
        val pending = AuthorityPendingTransition("DEVICE_GRANT", 2, 1_800_000_000, A_PENDING_RECORD_HASH)

        val result = manager.opposeWithRecord(A_TOKEN, aRootedChain(pending = pending))

        assertThat(result.exceptionOrNull())
            .isInstanceOf(AuthorityError.OppositionDeviceRequired::class.java)
        assertThat(client.challengeCalls).isEmpty()
    }

    @Test
    fun `a malformed recovery artifact is refused locally, before a step-up is spent`() = runTest {
        val client = FakeAccountAuthorityClient()
        val manager = DefaultAccountAuthorityManager(client, createKeyStore())

        val result = manager.beginRecovery("not-a-recovery-key")

        assertThat((result.exceptionOrNull() as InvalidAuthorityRecordException).reason)
            .isEqualTo("bad_artifact_prefix")
        assertThat(client.challengeCalls).isEmpty()
    }

    @Test
    fun `a recovery is signed by the artifact's key and installs a new pair`() = runTest {
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(client, keyStore)
        // The artifact an earlier adoption on some other device handed its owner.
        val oldRecovery = Ed25519Sign.KeyPair.newKeyPair()
        val artifact = RecoveryArtifact.encode(oldRecovery.privateKey)

        val offer = manager.beginRecovery(artifact).getOrThrow()
        val installed = checkNotNull(keyStore.adoptionKeys())
        manager.recoverAuthority(
            accessToken = A_TOKEN,
            chain = aRootedChain(headSeq = 1),
            recoveryArtifact = artifact,
            deviceLabel = "Pixel 9",
            stepUp = A_PIN,
            artifactConfirmed = true,
        ).getOrThrow()

        assertThat(offer.recoveryArtifact).startsWith(RecoveryArtifact.PREFIX)
        assertThat(offer.recoveryArtifact).isNotEqualTo(artifact)
        assertThat(client.challengeCalls.last().purpose).isEqualTo(AuthorityPurpose.RECOVER)
        val submission = client.recoverCalls.single()
        val record = decode(submission.recordB64Url)
        assertThat(record).hasLength(209)
        assertThat(record.copyOfRange(80, 112)).isEqualTo(installed.deviceAuthorityPublicKey())
        assertThat(record.copyOfRange(112, 144)).isEqualTo(installed.recoveryAuthorityPublicKey())
        assertThat(record[176].toInt()).isEqualTo(AuthorityRecord.AUTHORIZATION_RECOVERY_KEY)
        assertThat(record.copyOfRange(177, 209)).isEqualTo(oldRecovery.publicKey)
        // Signed by the OLD recovery key, which is the whole reason the artifact matters.
        val preimage = AuthorityProofs.recordPreimage(
            AuthorityRecordType.AUTHORITY_RECOVERY,
            decode(FakeAccountAuthorityClient.A_CHALLENGE_B64),
            record,
        )
        assertThat(verify(oldRecovery.publicKey, preimage, decode(submission.signatureB64Url))).isTrue()
        // And the pair the record installs is this device's authority afterwards.
        assertThat(keyStore.authorityDevicePublicKey()).isEqualTo(installed.deviceAuthorityPublicKey())
    }

    @Test
    fun `a recovery whose artifact was not confirmed never reaches the server`() = runTest {
        val client = FakeAccountAuthorityClient()
        val manager = DefaultAccountAuthorityManager(client, createKeyStore())
        val artifact = RecoveryArtifact.encode(Ed25519Sign.KeyPair.newKeyPair().privateKey)
        manager.beginRecovery(artifact).getOrThrow()

        val result = manager.recoverAuthority(
            A_TOKEN,
            aRootedChain(),
            artifact,
            "Pixel 9",
            A_PIN,
            artifactConfirmed = false,
        )

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthorityError.ArtifactUnconfirmed::class.java)
        assertThat(client.recoverCalls).isEmpty()
    }

    @Test
    fun `a registration from a device with an authority key is bound to it by a signature`() = runTest {
        val client = FakeAccountAuthorityClient(stateResult = { Result.success(aRootedChain()) })
        val keyStore = createKeyStore()
        val manager = adoptedManager(client, keyStore)
        val ourKey = checkNotNull(keyStore.authorityDevicePublicKey())

        manager.registerSecurityNotifications(
            accessToken = A_TOKEN,
            pushToken = "an-fcm-token",
            platform = SecurityNotificationRegistration.PLATFORM_FCM,
            appId = "global.gua.android",
            deviceLabel = "Pixel 9",
        ).getOrThrow()

        val registration = client.registerNotificationCalls.single()
        assertThat(registration.installationId).isEqualTo(manager.installationId())
        assertThat(registration.authorityDeviceKeyB64Url).isEqualTo(encode(ourKey))
        assertThat(client.challengeCalls.last().purpose).isEqualTo(AuthorityPurpose.NOTIFY)
        val preimage = AuthorityProofs.notificationPreimage(
            accountReference = AccountId.parse(A_BOOTSTRAP_ACCOUNT_ID).rawBytes(),
            installationIdHash = sha256(manager.installationId().toByteArray()),
            deviceKey = ourKey,
            challenge = decode(FakeAccountAuthorityClient.A_CHALLENGE_B64),
        )
        assertThat(verify(ourKey, preimage, decode(checkNotNull(registration.signatureB64Url)))).isTrue()
    }

    @Test
    fun `an install with no authority key registers unbound, and its id survives a clear`() = runTest {
        val client = FakeAccountAuthorityClient()
        val keyStore = createKeyStore()
        val manager = DefaultAccountAuthorityManager(client, keyStore)

        manager.registerSecurityNotifications(
            A_TOKEN,
            "an-fcm-token",
            SecurityNotificationRegistration.PLATFORM_FCM,
            "global.gua.android",
            "Pixel 9",
        ).getOrThrow()
        val first = manager.installationId()
        // Signing out forgets the signup slot. It must not forget this: the registration has to outlive the
        // sessions a completed account recovery revokes, which is the whole reason it is not a pusher.
        keyStore.clear()

        val registration = client.registerNotificationCalls.single()
        assertThat(registration.authorityDeviceKeyB64Url).isNull()
        assertThat(registration.signatureB64Url).isNull()
        assertThat(client.challengeCalls).isEmpty()
        assertThat(manager.installationId()).isEqualTo(first)
    }

    @Test
    fun `a granted candidate becomes this device's authority the next time the chain is read`() = runTest {
        val keyStore = createKeyStore()
        val offered = keyStore.createCandidateKey()
        val client = FakeAccountAuthorityClient(
            stateResult = {
                Result.success(
                    aRootedChain(
                        devices = listOf(
                            anAuthorityDevice(deviceKeyB64Url = encode(offered), state = "QUARANTINED"),
                        ),
                    )
                )
            },
        )
        val manager = DefaultAccountAuthorityManager(client, keyStore)
        assertThat(manager.holdsAuthority()).isFalse()

        manager.state(A_TOKEN).getOrThrow()

        // A grant is signed by ANOTHER device, so this one never sees the record: what it sees is its own key
        // in the device set. Quarantined counts, because the window withholds what the device may sign rather
        // than whether the key is this account's.
        assertThat(manager.holdsAuthority()).isTrue()
        assertThat(keyStore.authorityDevicePublicKey()).isEqualTo(offered)
        assertThat(keyStore.candidateDevicePublicKey()).isNull()
    }

    @Test
    fun `a candidate the chain has not granted stays a candidate`() = runTest {
        val keyStore = createKeyStore()
        keyStore.createCandidateKey()
        val client = FakeAccountAuthorityClient(
            stateResult = { Result.success(aRootedChain(devices = listOf(anAuthorityDevice()))) },
        )
        val manager = DefaultAccountAuthorityManager(client, keyStore)

        manager.state(A_TOKEN).getOrThrow()

        assertThat(manager.holdsAuthority()).isFalse()
        assertThat(keyStore.candidateDevicePublicKey()).isNotNull()
    }

    private fun aCandidate(
        key: ByteArray = A_GRANTEE_KEY,
        fingerprint: String = AuthorityFingerprint.of(A_GRANTEE_KEY),
    ) = AuthorityCandidate(
        deviceKeyB64Url = encode(key),
        fingerprint = fingerprint,
        label = "Pixel Tablet",
        expiresAtEpochSeconds = 0,
    )

    /** A manager whose device has adopted, which is the state every device-signed transition starts from. */
    private suspend fun adoptedManager(
        client: FakeAccountAuthorityClient,
        keyStore: AccountAuthorityKeyStore = createKeyStore(),
    ): AccountAuthorityManager = DefaultAccountAuthorityManager(client, keyStore).also { manager ->
        manager.beginAdoption().getOrThrow()
        manager.adopt(A_TOKEN, aBootstrapChain(), "Pixel 9", A_PIN, artifactConfirmed = true).getOrThrow()
    }

    private fun sha256(value: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(value)

    private fun createKeyStore(): DefaultAccountAuthorityKeyStore = DefaultAccountAuthorityKeyStore(
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
        private val A_PIN = AuthorityStepUp.Pin("123456")

        /** A real curve point, since the codec refuses anything else. */
        private val A_GRANTEE_KEY = Ed25519Sign.KeyPair.newKeyPair().publicKey

        /** 32 bytes of hex, as a pending record's hash is reported. */
        private const val A_PENDING_RECORD_HASH =
            "9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a0"
    }
}
