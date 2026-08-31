/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Gua: sets up key storage (key backup + recovery / secret-storage) entirely in the background so
 * the user never has to go through the "Confirm your digital identity" verification ceremony.
 *
 * This mirrors iOS `UserSessionStore.bootstrapKeyStorageIfNeeded` /
 * `restoreKeyStorageIfNeeded` exactly:
 *
 *  - FRESH IDENTITY (first sign-in, or a returning user on a brand-new device with no reachable
 *    backup): recovery is not yet enabled, so we silently enable it. The Rust SDK has already
 *    enabled key backup + cross-signing via `ClientBuilder.autoEnableBackups` /
 *    `autoEnableCrossSigning`; `enableRecovery` then generates the recovery key and provisions
 *    secret storage. This is the equivalent of iOS `secureBackupController.enable()` +
 *    `generateRecoveryKey()` + `confirmRecoveryKey()`.
 *
 *  - RESTORE (re-login on the SAME device): the encrypted crypto / secret-storage SQLite store is
 *    durable across re-login, so the SDK reports recovery as already `ENABLED` and we no-op. This
 *    is the Android equivalent of iOS reading the stored recovery key from the keychain and calling
 *    `confirmRecoveryKey` — here the store itself plays the role of the keychain, so there is
 *    nothing to re-confirm.
 *
 * CRYPTO SEMANTICS FOR OLD HISTORY (matches iOS):
 *  - Same-device re-login: secret storage persists, key backup is reachable, old encrypted history
 *    is decryptable. No friction, no UI.
 *  - Brand-new device with no recovery key available: a FRESH crypto identity is provisioned. Old
 *    encrypted history is NOT silently restored (iOS behaves identically — restore only happens
 *    from a device-local recovery key, which a fresh device does not have). The user lands straight
 *    in the app; to recover old history they can verify with another device or enter their recovery
 *    key from Settings > Encryption. We never gate them on it.
 *
 * The whole thing runs detached in the session scope and is fully fail-safe: any failure is logged
 * and swallowed so it can never block the user from landing in the app or produce a re-prompt.
 *
 * Device verification / recovery remains reachable from Settings (SecureBackup feature); this only
 * removes the *forced onboarding* gate, not the capability.
 */
internal class SilentSessionEncryptionBootstrapper(
    private val sessionId: SessionId,
    private val encryptionService: EncryptionService,
    private val sessionCoroutineScope: CoroutineScope,
) {
    fun start() {
        sessionCoroutineScope.launch {
            try {
                // Wait until the SDK has had a chance to report a real recovery state (i.e. after the
                // first sync). WAITING_FOR_SYNC / UNKNOWN are transient bootstrapping values.
                val recoveryState = encryptionService.recoveryStateStateFlow
                    .first { it != RecoveryState.WAITING_FOR_SYNC && it != RecoveryState.UNKNOWN }

                if (recoveryState == RecoveryState.ENABLED) {
                    // Restore path: secret storage already provisioned (same-device re-login). Nothing
                    // to do — mirrors iOS guarding on `recoveryState.value == .enabled`.
                    Timber.tag(TAG).i("Recovery already enabled for %s, key storage restore is a no-op.", sessionId.value)
                    return@launch
                }

                // Bootstrap path: provision recovery silently.
                Timber.tag(TAG).i("Bootstrapping key storage silently for %s (recoveryState=%s).", sessionId.value, recoveryState)
                encryptionService.enableRecovery(waitForBackupsToUpload = false)
                    .onSuccess {
                        Timber.tag(TAG).i("Finished bootstrapping key storage for %s.", sessionId.value)
                    }
                    .onFailure { error ->
                        // GUA FORK: enableRecovery refuses to run when a key backup already
                        // exists on the server, which is exactly the state accounts damaged by
                        // the earlier version of this bootstrapper are in. It fails, and without
                        // the branch below the account stays stuck at INCOMPLETE forever, so an
                        // app update repairs nobody.
                        Timber.tag(TAG).w(error, "Could not enable recovery for %s; checking whether a backup blocks it.", sessionId.value)
                        repairBlockedByExistingBackup()
                    }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Fail-safe: never let key-storage setup block the user.
                Timber.tag(TAG).e(error, "Unexpected error while bootstrapping key storage for %s.", sessionId.value)
            }
        }
    }

    // / Repairs an account whose key storage cannot be enabled because a backup already exists.
    private suspend fun repairBlockedByExistingBackup() {
        if (encryptionService.doesBackupExistOnServer().getOrDefault(false).not()) {
            Timber.tag(TAG).w("No server backup, so the failure was not a blocked backup; leaving it alone.")
            return
        }

        // Another signed-in device may still hand the secrets over, which repairs this for free
        // and keeps the backup. Wait for that to actually happen rather than asking whether such
        // a device exists: the device list keeps stale entries that will never respond, so the
        // existence check answers "yes" forever and no account is ever repaired.
        if (withTimeoutOrNull(HANDOVER_TIMEOUT) {
                encryptionService.recoveryStateStateFlow.first { it == RecoveryState.ENABLED }
                true
            } == true) {
            Timber.tag(TAG).i("Another device supplied the secrets; key storage is healthy.")
            return
        }

        // No key is stored anywhere on Android and no other device exists, so nothing can ever
        // decrypt that backup again. Keeping it preserves only a permanently broken account.
        // This does not touch the cross-signing identity, so no contact is warned.
        Timber.tag(TAG).w("Backup is unreachable by any key or device; replacing key storage.")
        encryptionService.disableRecovery()
            .onFailure { error ->
                Timber.tag(TAG).e(error, "Failed disabling unreachable key storage.")
                return
            }
        encryptionService.enableRecovery(waitForBackupsToUpload = false)
            .onSuccess { Timber.tag(TAG).i("Replaced key storage for %s.", sessionId.value) }
            .onFailure { error -> Timber.tag(TAG).e(error, "Failed replacing key storage.") }
    }

    private companion object {
        const val TAG = "SilentEncryptionBootstrap"
        val HANDOVER_TIMEOUT = 15.seconds
    }
}
