/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.subtle.Ed25519Sign
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.cryptography.api.EncryptionDecryptionService
import io.element.android.libraries.cryptography.api.EncryptionResult
import io.element.android.libraries.cryptography.api.SecretKeyRepository
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * GUA FORK: default [AccountAuthorityKeyStore].
 *
 * WHY THE SEED IS SEALED RATHER THAN KEYSTORE-RESIDENT. ADM-008 decision 5 fixes suite 0x01 as Ed25519,
 * and the Android keystore does not generate or sign with Ed25519 at this module's minSdk, so a
 * keystore-resident private key is not available to ask for. The equivalent used here is the pattern the
 * app already uses for the lock-screen PIN: the Ed25519 seed is sealed with AES-GCM under a key that is
 * generated inside the Android keystore and is itself non-exportable and hardware-backed where the
 * device offers it, and only the sealed blob is ever persisted. The seed is therefore never written in
 * the clear, never logged, and never leaves the process except as ciphertext only this device's keystore
 * key can open. The application sets `android:allowBackup="false"`, so nothing stored here is backed up
 * or transferred to another device, which is what keeps the key non-syncing.
 *
 * The suite byte reserves room for a hardware-resident P-256 suite, and that is the change that would
 * make the private half keystore-resident.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAccountAuthorityKeyStore(
    private val secretKeyRepository: SecretKeyRepository,
    private val encryptionDecryptionService: EncryptionDecryptionService,
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
) : AccountAuthorityKeyStore {
    private val dataStore = preferenceDataStoreFactory.create(STORE_NAME)
    private val mutex = Mutex()

    override suspend fun hasKeyPair(): Boolean = publicKeys() != null

    override suspend fun createKeyPair(): AccountAuthorityPublicKeys = mutex.withLock {
        val authority = Ed25519Sign.KeyPair.newKeyPair()
        val recovery = Ed25519Sign.KeyPair.newKeyPair()
        // ADM-008 decision 4: the recovery authority key is generated on device and must differ from the
        // authority key. Two independent CSPRNG draws, checked rather than assumed.
        check(!authority.privateKey.contentEquals(recovery.privateKey)) { "the recovery key must differ from the authority key" }
        val secretKey = secretKeyRepository.getOrCreateKey(SECRET_KEY_ALIAS, false)
        val sealedAuthority = encryptionDecryptionService.encrypt(secretKey, authority.privateKey).toBase64()
        val sealedRecovery = encryptionDecryptionService.encrypt(secretKey, recovery.privateKey).toBase64()
        dataStore.edit { preferences ->
            preferences[sealedAuthoritySeedKey] = sealedAuthority
            preferences[sealedRecoverySeedKey] = sealedRecovery
        }
        AccountAuthorityPublicKeys(authority.publicKey, recovery.publicKey)
    }

    override suspend fun publicKeys(): AccountAuthorityPublicKeys? {
        val authoritySeed = readSeed(sealedAuthoritySeedKey) ?: return null
        val recoverySeed = readSeed(sealedRecoverySeedKey) ?: return null
        return AccountAuthorityPublicKeys(
            authority = Ed25519Sign.KeyPair.newKeyPairFromSeed(authoritySeed).publicKey,
            recovery = Ed25519Sign.KeyPair.newKeyPairFromSeed(recoverySeed).publicKey,
        )
    }

    override suspend fun signWithAuthorityKey(message: ByteArray): ByteArray {
        val seed = readSeed(sealedAuthoritySeedKey)
            ?: error("No account authority key is stored on this device")
        return Ed25519Sign(seed).sign(message)
    }

    override suspend fun clear() = mutex.withLock {
        dataStore.edit { preferences ->
            preferences.remove(sealedAuthoritySeedKey)
            preferences.remove(sealedRecoverySeedKey)
        }
        secretKeyRepository.deleteKey(SECRET_KEY_ALIAS)
    }

    /**
     * Unseals one stored seed, or returns null when none is stored or the keystore key that sealed it is
     * gone (a restored install, or a device where the user cleared credentials). A missing seed is
     * reported as "no key", never as a signature, because a silent bootstrap on a device that meant to
     * register a genesis is the failure mode ADM-008 warns about.
     */
    private suspend fun readSeed(key: androidx.datastore.preferences.core.Preferences.Key<String>): ByteArray? {
        val sealed = dataStore.data.first()[key] ?: return null
        return try {
            val secretKey = secretKeyRepository.getOrCreateKey(SECRET_KEY_ALIAS, false)
            encryptionDecryptionService.decrypt(secretKey, EncryptionResult.fromBase64(sealed))
        } catch (_: Throwable) {
            // Never log the failure with the value: it is key material.
            null
        }
    }

    private companion object {
        private const val STORE_NAME = "gua_account_genesis"
        private const val SECRET_KEY_ALIAS = "gua.SECRET_KEY_ALIAS_ACCOUNT_AUTHORITY"
        private val sealedAuthoritySeedKey = stringPreferencesKey("sealed_authority_seed")
        private val sealedRecoverySeedKey = stringPreferencesKey("sealed_recovery_seed")
    }
}
