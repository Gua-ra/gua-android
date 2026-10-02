/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import androidx.datastore.preferences.core.Preferences
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
 * The Android keystore does not generate or sign with Ed25519 at this module's minSdk, so the private
 * key cannot be keystore-resident. Instead the Ed25519 seed is sealed with AES-GCM under a
 * non-exportable keystore key (hardware-backed where the device offers it), and only the sealed blob
 * is persisted. The application sets `android:allowBackup="false"`, so nothing stored here is backed
 * up or transferred to another device.
 *
 * Both slots are sealed under the same keystore key, so [clear] deletes that key only once nothing is
 * attached: deleting it earlier would leave an attached blob nothing can open.
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
        // The recovery key must differ from the authority key.
        check(!authority.privateKey.contentEquals(recovery.privateKey)) { "the recovery key must differ from the authority key" }
        val secretKey = secretKeyRepository.getOrCreateKey(SECRET_KEY_ALIAS, false)
        val sealedAuthority = encryptionDecryptionService.encrypt(secretKey, authority.privateKey).toBase64()
        val sealedRecovery = encryptionDecryptionService.encrypt(secretKey, recovery.privateKey).toBase64()
        dataStore.edit { preferences ->
            // The signup slot only. The attached entries are never written from here.
            preferences[sealedAuthoritySeedKey] = sealedAuthority
            preferences[sealedRecoverySeedKey] = sealedRecovery
        }
        AccountAuthorityPublicKeys(authority.publicKey, recovery.publicKey)
    }

    override suspend fun publicKeys(): AccountAuthorityPublicKeys? =
        publicKeysOf(sealedAuthoritySeedKey, sealedRecoverySeedKey)

    override suspend fun markAttached(accountId: AccountId) {
        mutex.withLock {
            val preferences = dataStore.data.first()
            val alreadyAttached = preferences[attachedAccountIdKey]
            if (alreadyAttached != null && alreadyAttached != accountId.value) {
                error("Another account is already attached on this device")
            }
            if (alreadyAttached == accountId.value && preferences[attachedAuthoritySeedKey] != null) {
                // Already recorded, so a retried attach step is a no-op rather than a second promotion.
                return@withLock
            }
            val sealedAuthority = preferences[sealedAuthoritySeedKey]
                ?: error("No account authority key is stored on this device")
            val sealedRecovery = preferences[sealedRecoverySeedKey]
                ?: error("No recovery authority key is stored on this device")
            dataStore.edit { edited ->
                edited[attachedAccountIdKey] = accountId.value
                edited[attachedAuthoritySeedKey] = sealedAuthority
                edited[attachedRecoverySeedKey] = sealedRecovery
                // The pair is the account's now, not the signup's, so the signup slot is emptied.
                edited.remove(sealedAuthoritySeedKey)
                edited.remove(sealedRecoverySeedKey)
            }
        }
    }

    override suspend fun attachedAccountId(): String? = dataStore.data.first()[attachedAccountIdKey]

    override suspend fun attachedPublicKeys(): AccountAuthorityPublicKeys? =
        publicKeysOf(attachedAuthoritySeedKey, attachedRecoverySeedKey)

    override suspend fun signWithAuthorityKey(message: ByteArray): ByteArray {
        val seed = readSeed(sealedAuthoritySeedKey)
            ?: error("No account authority key is stored on this device")
        return Ed25519Sign(seed).sign(message)
    }

    override suspend fun clear() {
        mutex.withLock {
            dataStore.edit { preferences ->
                // The signup slot only: an attached pair is an account's authority and outlives a signup.
                preferences.remove(sealedAuthoritySeedKey)
                preferences.remove(sealedRecoverySeedKey)
            }
            if (dataStore.data.first()[attachedAccountIdKey] == null) {
                secretKeyRepository.deleteKey(SECRET_KEY_ALIAS)
            }
        }
    }

    private suspend fun publicKeysOf(
        authorityKey: Preferences.Key<String>,
        recoveryKey: Preferences.Key<String>,
    ): AccountAuthorityPublicKeys? {
        val authoritySeed = readSeed(authorityKey) ?: return null
        val recoverySeed = readSeed(recoveryKey) ?: return null
        return AccountAuthorityPublicKeys(
            authority = Ed25519Sign.KeyPair.newKeyPairFromSeed(authoritySeed).publicKey,
            recovery = Ed25519Sign.KeyPair.newKeyPairFromSeed(recoverySeed).publicKey,
        )
    }

    /**
     * Unseals one stored seed. Returns null when none is stored or the keystore key that sealed it is
     * gone (a restored install, or a device where the user cleared credentials). A missing seed is
     * reported as "no key", never as a signature.
     */
    private suspend fun readSeed(key: Preferences.Key<String>): ByteArray? {
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
        private val attachedAccountIdKey = stringPreferencesKey("attached_account_id")
        private val attachedAuthoritySeedKey = stringPreferencesKey("attached_authority_seed")
        private val attachedRecoverySeedKey = stringPreferencesKey("attached_recovery_seed")
    }
}
