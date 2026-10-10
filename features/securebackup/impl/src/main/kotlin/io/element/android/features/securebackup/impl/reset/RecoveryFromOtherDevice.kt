/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import io.element.android.features.securebackup.impl.R
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration

enum class RecoveryFromOtherDeviceOutcome {
    RECOVERED,

    /** The identity arrived, but the backup this device held before could not be confirmed as current. */
    BACKUP_UNCONFIRMED,
    KEYS_DID_NOT_ARRIVE,
}

/** GUA FORK: judges a recovery from another device of the account by what this device holds afterwards. */
class RecoveryFromOtherDevice(
    private val encryptionService: EncryptionService,
    private val snackbarDispatcher: SnackbarDispatcher,
) {
    private var heldBackupBeforeVerification = false

    /**
     * Must run before each verification starts. The SDK asks the other device for a backup key only when
     * this one holds none, so a backup enabled now is not refreshed by the verification and may be one a
     * reset elsewhere deleted.
     */
    fun recordStateBeforeVerification() {
        heldBackupBeforeVerification = encryptionService.backupStateStateFlow.value == BackupState.ENABLED
    }

    /**
     * The SDK reports recovery enabled from the local backup alone. Confirming a backup held before the
     * verification against the server takes a stored recovery key, and this app stores none.
     */
    suspend fun awaitOutcome(timeout: Duration): RecoveryFromOtherDeviceOutcome {
        val recoveryEnabled = withTimeoutOrNull(timeout) {
            encryptionService.recoveryStateStateFlow.first { it == RecoveryState.ENABLED }
        } != null
        return when {
            !recoveryEnabled -> RecoveryFromOtherDeviceOutcome.KEYS_DID_NOT_ARRIVE
            heldBackupBeforeVerification -> RecoveryFromOtherDeviceOutcome.BACKUP_UNCONFIRMED
            else -> RecoveryFromOtherDeviceOutcome.RECOVERED
        }
    }

    /** Only keys that did not arrive keep the reset screen up. */
    fun report(outcome: RecoveryFromOtherDeviceOutcome, closeFlow: () -> Unit) {
        when (outcome) {
            RecoveryFromOtherDeviceOutcome.RECOVERED -> {
                Timber.d("Keys arrived from the other device")
                closeFlow()
            }
            RecoveryFromOtherDeviceOutcome.BACKUP_UNCONFIRMED -> {
                Timber.w("Keys arrived from the other device, but the backup held before could not be confirmed as current")
                snackbarDispatcher.post(SnackbarMessage(R.string.gua_encryption_recover_from_other_device_backup_unconfirmed))
                // The reset offered on this screen would delete the account's current backup.
                closeFlow()
            }
            RecoveryFromOtherDeviceOutcome.KEYS_DID_NOT_ARRIVE -> {
                Timber.w("Keys did not arrive from the other device in time")
                snackbarDispatcher.post(SnackbarMessage(R.string.gua_encryption_recover_from_other_device_failed))
            }
        }
    }
}
