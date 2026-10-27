/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import com.google.common.truth.Truth.assertThat
import io.element.android.features.securebackup.impl.R
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RecoveryFromOtherDeviceTest {
    private val encryptionService = FakeEncryptionService()
    private val snackbarDispatcher = SnackbarDispatcher()
    private val recovery = RecoveryFromOtherDevice(encryptionService, snackbarDispatcher)

    @Test
    fun `keys arriving on a device that held no backup close the flow as a recovery`() = runTest {
        encryptionService.emitBackupState(BackupState.UNKNOWN)
        encryptionService.emitRecoveryState(RecoveryState.INCOMPLETE)

        recovery.recordStateBeforeVerification()
        val outcome = async { recovery.awaitOutcome(CEILING) }
        runCurrent()
        // The other device hands over the identity and the current backup key.
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.ENABLED)

        assertThat(outcome.await()).isEqualTo(RecoveryFromOtherDeviceOutcome.RECOVERED)
        var closed = false
        recovery.report(outcome.await()) { closed = true }
        assertThat(closed).isTrue()
        assertThat(snackbarDispatcher.snackbarMessage.first()).isNull()
    }

    @Test
    fun `a backup held before the verification that cannot be confirmed closes the flow without a recovery`() = runTest {
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.INCOMPLETE)

        recovery.recordStateBeforeVerification()
        encryptionService.emitRecoveryState(RecoveryState.ENABLED)
        val outcome = recovery.awaitOutcome(CEILING)

        assertThat(outcome).isEqualTo(RecoveryFromOtherDeviceOutcome.BACKUP_UNCONFIRMED)
        var closed = false
        recovery.report(outcome) { closed = true }
        assertThat(closed).isTrue()
        assertThat(snackbarDispatcher.snackbarMessage.first()?.messageResId)
            .isEqualTo(R.string.gua_encryption_recover_from_other_device_backup_unconfirmed)
    }

    @Test
    fun `each attempt judges the backup held when it started`() = runTest {
        encryptionService.emitRecoveryState(RecoveryState.INCOMPLETE)
        encryptionService.emitBackupState(BackupState.ENABLED)
        recovery.recordStateBeforeVerification()

        encryptionService.emitBackupState(BackupState.UNKNOWN)
        recovery.recordStateBeforeVerification()
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.ENABLED)

        assertThat(recovery.awaitOutcome(CEILING)).isEqualTo(RecoveryFromOtherDeviceOutcome.RECOVERED)
    }

    @Test
    fun `recovery that never becomes enabled keeps the reset screen up`() = runTest {
        encryptionService.emitRecoveryState(RecoveryState.INCOMPLETE)

        listOf(BackupState.UNKNOWN, BackupState.ENABLED).forEach { backupState ->
            encryptionService.emitBackupState(backupState)
            recovery.recordStateBeforeVerification()
            assertThat(recovery.awaitOutcome(CEILING)).isEqualTo(RecoveryFromOtherDeviceOutcome.KEYS_DID_NOT_ARRIVE)
        }
        var closed = false
        recovery.report(RecoveryFromOtherDeviceOutcome.KEYS_DID_NOT_ARRIVE) { closed = true }
        assertThat(closed).isFalse()
        assertThat(snackbarDispatcher.snackbarMessage.first()?.messageResId)
            .isEqualTo(R.string.gua_encryption_recover_from_other_device_failed)
    }

    private companion object {
        val CEILING = 60.seconds
    }
}
