/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

enum class RecoveryFromOtherDeviceOutcome {
    RECOVERED,
    BACKUP_NOT_RESTORED,
    KEYS_DID_NOT_ARRIVE,
}

/** GUA FORK: judges a recovery from another device of the account by what this device holds afterwards. */
class RecoveryFromOtherDevice(
    private val encryptionService: EncryptionService,
    private val sessionVerificationService: SessionVerificationService,
) {
    // Latched: once the identity arrives, this device is signed again and the signal is gone.
    private var holdsDeletedBackupKey = false

    /**
     * Must run before the verification starts. Backup on while this device is no longer signed by the
     * account's identity means the identity was reset elsewhere, and a reset deletes the backup this
     * device holds the key of. The SDK requests a backup key from another device only when it holds none.
     */
    fun recordStateBeforeVerification() {
        if (encryptionService.backupStateStateFlow.value == BackupState.ENABLED &&
            sessionVerificationService.sessionVerifiedStatus.value == SessionVerifiedStatus.NotVerified
        ) {
            holdsDeletedBackupKey = true
        }
    }

    /** The SDK reports recovery enabled from the local backup alone, so with a deleted backup's key it only means the identity arrived. */
    suspend fun awaitOutcome(timeout: Duration): RecoveryFromOtherDeviceOutcome {
        val recoveryEnabled = withTimeoutOrNull(timeout) {
            encryptionService.recoveryStateStateFlow.first { it == RecoveryState.ENABLED }
        } != null
        return when {
            !recoveryEnabled -> RecoveryFromOtherDeviceOutcome.KEYS_DID_NOT_ARRIVE
            holdsDeletedBackupKey -> RecoveryFromOtherDeviceOutcome.BACKUP_NOT_RESTORED
            else -> RecoveryFromOtherDeviceOutcome.RECOVERED
        }
    }
}
