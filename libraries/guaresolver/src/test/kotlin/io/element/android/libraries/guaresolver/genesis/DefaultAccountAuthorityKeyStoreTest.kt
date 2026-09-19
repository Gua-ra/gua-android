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
import io.element.android.libraries.cryptography.api.EncryptionResult
import io.element.android.libraries.cryptography.impl.AESEncryptionDecryptionService
import io.element.android.libraries.cryptography.test.SimpleSecretKeyRepository
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * GUA FORK: the account authority key store round trip (ADM-008 decision 5).
 *
 * Signatures are verified with the JDK Ed25519 provider rather than with the same library that produced
 * them, so this really checks interoperability with the server that will verify them, not that one
 * implementation agrees with itself.
 */
class DefaultAccountAuthorityKeyStoreTest {
    @Test
    fun `creating a key pair yields two distinct 32-byte public keys`() = runTest {
        val keyStore = createKeyStore()

        val keys = keyStore.createKeyPair()

        assertThat(keys.authorityPublicKey()).hasLength(32)
        assertThat(keys.recoveryAuthorityPublicKey()).hasLength(32)
        // ADM-008 decision 4: the recovery key must differ from the authority key.
        assertThat(keys.authorityPublicKey()).isNotEqualTo(keys.recoveryAuthorityPublicKey())
        // Both must be genuine curve points, or the server's decoder would refuse the genesis.
        assertThat(Ed25519PublicKeys.isOnCurve(keys.authorityPublicKey())).isTrue()
        assertThat(Ed25519PublicKeys.isOnCurve(keys.recoveryAuthorityPublicKey())).isTrue()
        assertThat(Ed25519PublicKeys.isAllZero(keys.authorityPublicKey())).isFalse()
    }

    @Test
    fun `a signature made by the stored key verifies under the public key it published`() = runTest {
        val keyStore = createKeyStore()
        val keys = keyStore.createKeyPair()
        val message = "the preimage the server will verify".toByteArray()

        val signature = keyStore.signWithAuthorityKey(message)

        assertThat(signature).hasLength(64)
        assertThat(verify(keys.authorityPublicKey(), message, signature)).isTrue()
        // The recovery key is committed, not used to sign: a proof under it must not pass as authority.
        assertThat(verify(keys.recoveryAuthorityPublicKey(), message, signature)).isFalse()
    }

    @Test
    fun `the stored pair survives a new instance over the same store`() = runTest {
        val factory = CachingPreferenceDataStoreFactory()
        val secretKeyRepository = SimpleSecretKeyRepository()
        val created = createKeyStore(factory, secretKeyRepository).createKeyPair()

        // A second instance reads what the first one sealed, which is what a restarted app does.
        val reopened = createKeyStore(factory, secretKeyRepository)

        assertThat(reopened.hasKeyPair()).isTrue()
        val keys = checkNotNull(reopened.publicKeys())
        assertThat(keys.authorityPublicKey()).isEqualTo(created.authorityPublicKey())
        assertThat(keys.recoveryAuthorityPublicKey()).isEqualTo(created.recoveryAuthorityPublicKey())

        val message = "signed after a restart".toByteArray()
        assertThat(verify(created.authorityPublicKey(), message, reopened.signWithAuthorityKey(message))).isTrue()
    }

    @Test
    fun `no key pair is reported before one is created`() = runTest {
        val keyStore = createKeyStore()

        assertThat(keyStore.hasKeyPair()).isFalse()
        assertThat(keyStore.publicKeys()).isNull()
    }

    @Test
    fun `signing without a stored key fails instead of returning something`() = runTest {
        val keyStore = createKeyStore()

        val thrown = runCatchingExceptions { keyStore.signWithAuthorityKey("anything".toByteArray()) }.exceptionOrNull()

        // A missing key must never be mistaken for a successful signature: that is the silent bootstrap
        // ADM-008 decision 6 warns about.
        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `clearing forgets the pair`() = runTest {
        val keyStore = createKeyStore()
        keyStore.createKeyPair()

        keyStore.clear()

        assertThat(keyStore.hasKeyPair()).isFalse()
        assertThat(keyStore.publicKeys()).isNull()
    }

    @Test
    fun `the seed is persisted only as ciphertext under the keystore-held key`() = runTest {
        val factory = CachingPreferenceDataStoreFactory()
        val secretKeyRepository = SimpleSecretKeyRepository()
        val encryptionDecryptionService = AESEncryptionDecryptionService()
        val keyStore = DefaultAccountAuthorityKeyStore(secretKeyRepository, encryptionDecryptionService, factory)

        val keys = keyStore.createKeyPair()

        val persisted = factory.create("gua_account_genesis").data.first()[stringPreferencesKey("sealed_authority_seed")]
        assertThat(persisted).isNotNull()

        // Unsealing with the same keystore-held key gives back the seed whose public half was published,
        // which is what proves the stored blob is the key and that it was stored sealed.
        val seed = encryptionDecryptionService.decrypt(
            secretKeyRepository.getOrCreateKey(SECRET_KEY_ALIAS, false),
            EncryptionResult.fromBase64(persisted!!),
        )
        assertThat(seed).hasLength(32)
        assertThat(Ed25519Sign.KeyPair.newKeyPairFromSeed(seed).publicKey).isEqualTo(keys.authorityPublicKey())

        // And the seed itself appears nowhere in what was written.
        assertThat(persisted).doesNotContain(seed.toHexForTest())
        assertThat(persisted).doesNotContain(String(seed, Charsets.ISO_8859_1))
    }

    @Test
    fun `the next signup does not touch the key pair of an attached account`() = runTest {
        val keyStore = createKeyStore()
        val attached = keyStore.createKeyPair()
        keyStore.markAttached(AN_ACCOUNT_ID)

        // A second signup on the same device: this is the call that used to overwrite both seeds.
        val forTheNextSignup = keyStore.createKeyPair()

        assertThat(forTheNextSignup.authorityPublicKey()).isNotEqualTo(attached.authorityPublicKey())
        val kept = checkNotNull(keyStore.attachedPublicKeys())
        assertThat(kept.authorityPublicKey()).isEqualTo(attached.authorityPublicKey())
        // Framework 0x01 seals the recovery key beside it, so it has to survive the same way.
        assertThat(kept.recoveryAuthorityPublicKey()).isEqualTo(attached.recoveryAuthorityPublicKey())
        assertThat(keyStore.attachedAccountId()).isEqualTo(AN_ACCOUNT_ID.value)
    }

    @Test
    fun `clearing the signup pair keeps the attached one and the key that seals it`() = runTest {
        val keyStore = createKeyStore()
        val attached = keyStore.createKeyPair()
        keyStore.markAttached(AN_ACCOUNT_ID)
        keyStore.createKeyPair()

        // The no-genesis branch calls this after minting a pair. It must not take the account with it.
        keyStore.clear()

        assertThat(keyStore.hasKeyPair()).isFalse()
        val kept = checkNotNull(keyStore.attachedPublicKeys())
        assertThat(kept.authorityPublicKey()).isEqualTo(attached.authorityPublicKey())
        assertThat(keyStore.attachedAccountId()).isEqualTo(AN_ACCOUNT_ID.value)
    }

    @Test
    fun `attaching a second account is refused instead of overwriting the first`() = runTest {
        val keyStore = createKeyStore()
        val attached = keyStore.createKeyPair()
        keyStore.markAttached(AN_ACCOUNT_ID)
        keyStore.createKeyPair()

        val thrown = runCatchingExceptions { keyStore.markAttached(ANOTHER_ACCOUNT_ID) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(checkNotNull(keyStore.attachedPublicKeys()).authorityPublicKey())
            .isEqualTo(attached.authorityPublicKey())
        assertThat(keyStore.attachedAccountId()).isEqualTo(AN_ACCOUNT_ID.value)
    }

    @Test
    fun `attaching the same account twice is a no-op`() = runTest {
        val keyStore = createKeyStore()
        val attached = keyStore.createKeyPair()
        keyStore.markAttached(AN_ACCOUNT_ID)

        keyStore.markAttached(AN_ACCOUNT_ID)

        assertThat(checkNotNull(keyStore.attachedPublicKeys()).authorityPublicKey())
            .isEqualTo(attached.authorityPublicKey())
    }

    @Test
    fun `attaching without a signup pair fails instead of recording an account with no key`() = runTest {
        val keyStore = createKeyStore()

        val thrown = runCatchingExceptions { keyStore.markAttached(AN_ACCOUNT_ID) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(keyStore.attachedAccountId()).isNull()
    }

    @Test
    fun `the attached pair survives a new instance over the same store`() = runTest {
        val factory = CachingPreferenceDataStoreFactory()
        val secretKeyRepository = SimpleSecretKeyRepository()
        val first = createKeyStore(factory, secretKeyRepository)
        val attached = first.createKeyPair()
        first.markAttached(AN_ACCOUNT_ID)

        val reopened = createKeyStore(factory, secretKeyRepository)

        assertThat(reopened.attachedAccountId()).isEqualTo(AN_ACCOUNT_ID.value)
        assertThat(checkNotNull(reopened.attachedPublicKeys()).authorityPublicKey())
            .isEqualTo(attached.authorityPublicKey())
    }

    private fun createKeyStore(
        preferenceDataStoreFactory: PreferenceDataStoreFactory = CachingPreferenceDataStoreFactory(),
        secretKeyRepository: SimpleSecretKeyRepository = SimpleSecretKeyRepository(),
    ) = DefaultAccountAuthorityKeyStore(
        secretKeyRepository = secretKeyRepository,
        encryptionDecryptionService = AESEncryptionDecryptionService(),
        preferenceDataStoreFactory = preferenceDataStoreFactory,
    )

    /** Verifies with the JDK provider, which is what identity-service verifies with. */
    private fun verify(rawPublicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val spki = SPKI_PREFIX.hexToBytesForTest() + rawPublicKey
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        return Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(message)
            verify(signature)
        }
    }

    private companion object {
        private const val SPKI_PREFIX = "302a300506032b6570032100"
        private const val SECRET_KEY_ALIAS = "gua.SECRET_KEY_ALIAS_ACCOUNT_AUTHORITY"

        // Derived rather than written out, so they are canonical by construction.
        private val AN_ACCOUNT_ID = AccountId.derive(AccountId.CLASS_GENESIS, "an attached account".toByteArray())
        private val ANOTHER_ACCOUNT_ID = AccountId.derive(AccountId.CLASS_GENESIS, "a second account".toByteArray())

        private fun String.hexToBytesForTest(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        private fun ByteArray.toHexForTest(): String = joinToString("") { "%02x".format(it) }
    }
}
