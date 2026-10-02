/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Ed25519Sign
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.cryptography.impl.AESEncryptionDecryptionService
import io.element.android.libraries.cryptography.test.SimpleSecretKeyRepository
import io.element.android.libraries.guaresolver.authority.RecoveryArtifact
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class AccountAuthorityKeyStoreAdoptionTest {
    @Test
    fun `an adoption mints two distinct curve points and an artifact that encodes the recovery key`() = runTest {
        val keyStore = createKeyStore()

        val keys = keyStore.createAdoptionKeys()

        assertThat(keys.deviceAuthorityPublicKey()).hasLength(32)
        assertThat(keys.recoveryAuthorityPublicKey()).hasLength(32)
        assertThat(keys.deviceAuthorityPublicKey()).isNotEqualTo(keys.recoveryAuthorityPublicKey())
        assertThat(Ed25519PublicKeys.isOnCurve(keys.deviceAuthorityPublicKey())).isTrue()
        assertThat(Ed25519PublicKeys.isOnCurve(keys.recoveryAuthorityPublicKey())).isTrue()
        assertThat(keys.recoveryArtifact).startsWith(RecoveryArtifact.PREFIX)
        val seed = decodeArtifact(keys.recoveryArtifact)
        assertThat(Ed25519Sign.KeyPair.newKeyPairFromSeed(seed).publicKey)
            .isEqualTo(keys.recoveryAuthorityPublicKey())
    }

    @Test
    fun `the adoption key signs, and the recovery key does not stand in for it`() = runTest {
        val keyStore = createKeyStore()
        val keys = keyStore.createAdoptionKeys()
        val message = "the preimage the server will verify".toByteArray()

        val signature = keyStore.signWithAdoptionKey(message)

        assertThat(signature).hasLength(64)
        assertThat(verify(keys.deviceAuthorityPublicKey(), message, signature)).isTrue()
        assertThat(verify(keys.recoveryAuthorityPublicKey(), message, signature)).isFalse()
    }

    @Test
    fun `adopting moves the pair where no later adoption can reach it`() = runTest {
        val keyStore = createKeyStore()
        val keys = keyStore.createAdoptionKeys()

        keyStore.markAdopted(AN_ACCOUNT_ID)

        assertThat(keyStore.adoptedAccountId()).isEqualTo(AN_ACCOUNT_ID)
        assertThat(keyStore.adoptionKeys()).isNull()
        assertThat(keyStore.authorityDevicePublicKey()).isEqualTo(keys.deviceAuthorityPublicKey())
        keyStore.markAdopted(AN_ACCOUNT_ID)
        assertThat(keyStore.authorityDevicePublicKey()).isEqualTo(keys.deviceAuthorityPublicKey())

        keyStore.createAdoptionKeys()
        assertThat(keyStore.authorityDevicePublicKey()).isEqualTo(keys.deviceAuthorityPublicKey())
    }

    @Test
    fun `a second account cannot take over this device's authority`() = runTest {
        val keyStore = createKeyStore()
        keyStore.createAdoptionKeys()
        keyStore.markAdopted(AN_ACCOUNT_ID)
        keyStore.createAdoptionKeys()

        val thrown = runCatchingExceptions { keyStore.markAdopted(ANOTHER_ACCOUNT_ID) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(keyStore.adoptedAccountId()).isEqualTo(AN_ACCOUNT_ID)
    }

    @Test
    fun `a genesis account's committed authority key is this device's authority key`() = runTest {
        val keyStore = createKeyStore()
        val genesisKeys = keyStore.createKeyPair()
        keyStore.markAttached(AccountId.parse(A_GENESIS_ACCOUNT_ID))

        assertThat(keyStore.authorityDevicePublicKey()).isEqualTo(genesisKeys.authorityPublicKey())
        val message = "an approval preimage".toByteArray()
        assertThat(verify(genesisKeys.authorityPublicKey(), message, keyStore.signAsAuthorityDevice(message)))
            .isTrue()
    }

    @Test
    fun `signing as an authority device fails rather than returning something when there is no key`() = runTest {
        val keyStore = createKeyStore()

        val thrown = runCatchingExceptions { keyStore.signAsAuthorityDevice("anything".toByteArray()) }
            .exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(keyStore.authorityDevicePublicKey()).isNull()
    }

    @Test
    fun `clearing a signup keeps the keystore key while an adopted pair depends on it`() = runTest {
        val factory = CachingPreferenceDataStoreFactory()
        val secretKeyRepository = SimpleSecretKeyRepository()
        val keyStore = createKeyStore(factory, secretKeyRepository)
        keyStore.createAdoptionKeys()
        keyStore.markAdopted(AN_ACCOUNT_ID)
        keyStore.createKeyPair()

        keyStore.clear()

        assertThat(keyStore.authorityDevicePublicKey()).isNotNull()
        assertThat(keyStore.hasKeyPair()).isFalse()
    }

    @Test
    fun `the adoption seeds are persisted only as ciphertext`() = runTest {
        val factory = CachingPreferenceDataStoreFactory()
        val keyStore = createKeyStore(factory)

        val keys = keyStore.createAdoptionKeys()

        val persisted = factory.create("gua_account_genesis").data.first()[stringPreferencesKey("adoption_device_seed")]
        assertThat(persisted).isNotNull()
        assertThat(persisted).doesNotContain(keys.deviceAuthorityPublicKey().joinToString(""))
    }

    private fun createKeyStore(
        preferenceDataStoreFactory: PreferenceDataStoreFactory = CachingPreferenceDataStoreFactory(),
        secretKeyRepository: SimpleSecretKeyRepository = SimpleSecretKeyRepository(),
    ) = DefaultAccountAuthorityKeyStore(
        secretKeyRepository = secretKeyRepository,
        encryptionDecryptionService = AESEncryptionDecryptionService(),
        preferenceDataStoreFactory = preferenceDataStoreFactory,
    )

    private fun decodeArtifact(artifact: String): ByteArray =
        Base32.decode(artifact.removePrefix(RecoveryArtifact.PREFIX).filterNot { it == ' ' })

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
        private val AN_ACCOUNT_ID =
            AccountId.derive(AccountId.CLASS_BOOTSTRAP, "an adopting account".toByteArray()).value
        private val ANOTHER_ACCOUNT_ID =
            AccountId.derive(AccountId.CLASS_BOOTSTRAP, "a second account".toByteArray()).value
        private val A_GENESIS_ACCOUNT_ID =
            AccountId.derive(AccountId.CLASS_GENESIS, "a genesis account".toByteArray()).value
    }
}
