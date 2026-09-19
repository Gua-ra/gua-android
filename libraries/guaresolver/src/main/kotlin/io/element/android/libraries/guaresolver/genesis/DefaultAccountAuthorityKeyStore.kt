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
import io.element.android.libraries.guaresolver.authority.RecoveryArtifact
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
 * The two slots are separate preference entries sealed under the SAME keystore key, which is why [clear]
 * deletes that key only once nothing is attached: deleting it while an attached pair is stored would
 * leave a blob nothing can open, which is the same thing as losing the account's authority.
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
            val remaining = dataStore.data.first()
            // GUA FORK: ADM-009. An adopted pair counts as much as an attached one here: deleting the
            // keystore key while either is stored would leave a blob nothing can open, which is the same
            // thing as losing the account's authority.
            if (remaining[attachedAccountIdKey] == null && remaining[adoptedAccountIdKey] == null) {
                secretKeyRepository.deleteKey(SECRET_KEY_ALIAS)
            }
        }
    }

    // GUA FORK: ADM-009, the authority chain's own slots. Sealed under the same keystore key as the genesis
    // slots, for the reason the interface gives.

    override suspend fun createAdoptionKeys(): AdoptionKeys = mutex.withLock {
        val device = Ed25519Sign.KeyPair.newKeyPair()
        val recovery = Ed25519Sign.KeyPair.newKeyPair()
        // ADM-009 decision 2: a record whose recovery key equals its device key is refused, so the two draws
        // are checked rather than assumed, exactly as the genesis pair is.
        check(!device.privateKey.contentEquals(recovery.privateKey)) {
            "the recovery authority key must differ from the device key"
        }
        val secretKey = secretKeyRepository.getOrCreateKey(SECRET_KEY_ALIAS, false)
        val sealedDevice = encryptionDecryptionService.encrypt(secretKey, device.privateKey).toBase64()
        val sealedRecovery = encryptionDecryptionService.encrypt(secretKey, recovery.privateKey).toBase64()
        dataStore.edit { preferences ->
            // The adoption slot only. The adopted entries are never written from here.
            preferences[adoptionDeviceSeedKey] = sealedDevice
            preferences[adoptionRecoverySeedKey] = sealedRecovery
        }
        AdoptionKeys(
            device = device.publicKey,
            recovery = recovery.publicKey,
            recoveryArtifact = RecoveryArtifact.encode(recovery.privateKey),
        )
    }

    override suspend fun adoptionKeys(): AdoptionKeys? {
        val deviceSeed = readSeed(adoptionDeviceSeedKey) ?: return null
        val recoverySeed = readSeed(adoptionRecoverySeedKey) ?: return null
        return AdoptionKeys(
            device = Ed25519Sign.KeyPair.newKeyPairFromSeed(deviceSeed).publicKey,
            recovery = Ed25519Sign.KeyPair.newKeyPairFromSeed(recoverySeed).publicKey,
            recoveryArtifact = RecoveryArtifact.encode(recoverySeed),
        )
    }

    override suspend fun markAdopted(accountId: String) {
        mutex.withLock {
            val preferences = dataStore.data.first()
            val alreadyAdopted = preferences[adoptedAccountIdKey]
            if (alreadyAdopted != null && alreadyAdopted != accountId) {
                error("Another account already holds this device's authority")
            }
            if (alreadyAdopted == accountId && preferences[adoptedDeviceSeedKey] != null) {
                // Already recorded, so a resubmitted or retried adoption is a no-op rather than a second
                // promotion over the pair the chain accepted.
                return@withLock
            }
            val sealedDevice = preferences[adoptionDeviceSeedKey]
                ?: error("No device authority key is stored on this device")
            val sealedRecovery = preferences[adoptionRecoverySeedKey]
                ?: error("No recovery authority key is stored on this device")
            dataStore.edit { edited ->
                edited[adoptedAccountIdKey] = accountId
                edited[adoptedDeviceSeedKey] = sealedDevice
                edited[adoptedRecoverySeedKey] = sealedRecovery
                edited.remove(adoptionDeviceSeedKey)
                edited.remove(adoptionRecoverySeedKey)
            }
        }
    }

    override suspend fun adoptedAccountId(): String? = dataStore.data.first()[adoptedAccountIdKey]

    override suspend fun authorityDevicePublicKey(): ByteArray? {
        val seed = readSeed(adoptedDeviceSeedKey) ?: readSeed(attachedAuthoritySeedKey) ?: return null
        return Ed25519Sign.KeyPair.newKeyPairFromSeed(seed).publicKey
    }

    override suspend fun signWithAdoptionKey(message: ByteArray): ByteArray {
        val seed = readSeed(adoptionDeviceSeedKey)
            ?: error("No device authority key is stored for an adoption on this device")
        return Ed25519Sign(seed).sign(message)
    }

    override suspend fun signAsAuthorityDevice(message: ByteArray): ByteArray {
        val seed = readSeed(adoptedDeviceSeedKey)
            ?: readSeed(attachedAuthoritySeedKey)
            ?: error("This device holds no account authority key")
        return Ed25519Sign(seed).sign(message)
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
     * Unseals one stored seed, or returns null when none is stored or the keystore key that sealed it is
     * gone (a restored install, or a device where the user cleared credentials). A missing seed is
     * reported as "no key", never as a signature, because a silent bootstrap on a device that meant to
     * register a genesis is the failure mode ADM-008 warns about.
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

        // GUA FORK: ADM-009. The authority chain's slots. Separate entries from the genesis ones, because an
        // adopted account holds authority its accountId does not commit and the two must never be mistaken
        // for each other.
        private val adoptionDeviceSeedKey = stringPreferencesKey("adoption_device_seed")
        private val adoptionRecoverySeedKey = stringPreferencesKey("adoption_recovery_seed")
        private val adoptedAccountIdKey = stringPreferencesKey("adopted_account_id")
        private val adoptedDeviceSeedKey = stringPreferencesKey("adopted_device_seed")
        private val adoptedRecoverySeedKey = stringPreferencesKey("adopted_recovery_seed")
    }
}
