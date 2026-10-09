/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RecoveryFromOtherDeviceTest {
    private val encryptionService = FakeEncryptionService()

    @Test
    fun `keys arriving on a device that held no backup key is a recovery`() = runTest {
        encryptionService.emitBackupState(BackupState.UNKNOWN)
        encryptionService.emitRecoveryState(RecoveryState.INCOMPLETE)
        val recovery = createRecovery(SessionVerifiedStatus.NotVerified)

        recovery.recordStateBeforeVerification()
        val outcome = async { recovery.awaitOutcome(CEILING) }
        runCurrent()
        encryptionService.emitRecoveryState(RecoveryState.ENABLED)

        assertThat(outcome.await()).isEqualTo(RecoveryFromOtherDeviceOutcome.RECOVERED)
    }

    @Test
    fun `recovery enabled on a device that kept the key of a deleted backup is not a recovery`() = runTest {
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.INCOMPLETE)
        val verificationService = FakeSessionVerificationService(initialSessionVerifiedStatus = SessionVerifiedStatus.NotVerified)
        val recovery = RecoveryFromOtherDevice(encryptionService, verificationService)

        recovery.recordStateBeforeVerification()
        verificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        encryptionService.emitRecoveryState(RecoveryState.ENABLED)

        assertThat(recovery.awaitOutcome(CEILING)).isEqualTo(RecoveryFromOtherDeviceOutcome.BACKUP_NOT_RESTORED)
        // The device is signed now, so a second attempt must still remember the deleted backup's key.
        recovery.recordStateBeforeVerification()
        assertThat(recovery.awaitOutcome(CEILING)).isEqualTo(RecoveryFromOtherDeviceOutcome.BACKUP_NOT_RESTORED)
    }

    @Test
    fun `a signed device with backup on is a recovery once recovery is enabled`() = runTest {
        encryptionService.emitBackupState(BackupState.ENABLED)
        encryptionService.emitRecoveryState(RecoveryState.ENABLED)

        listOf(SessionVerifiedStatus.Verified, SessionVerifiedStatus.Unknown).forEach { status ->
            val recovery = createRecovery(status)
            recovery.recordStateBeforeVerification()
            assertThat(recovery.awaitOutcome(CEILING)).isEqualTo(RecoveryFromOtherDeviceOutcome.RECOVERED)
        }
    }

    @Test
    fun `recovery that never becomes enabled means the keys did not arrive`() = runTest {
        encryptionService.emitRecoveryState(RecoveryState.INCOMPLETE)

        listOf(BackupState.UNKNOWN, BackupState.ENABLED).forEach { backupState ->
            encryptionService.emitBackupState(backupState)
            val recovery = createRecovery(SessionVerifiedStatus.NotVerified)
            recovery.recordStateBeforeVerification()
            assertThat(recovery.awaitOutcome(CEILING)).isEqualTo(RecoveryFromOtherDeviceOutcome.KEYS_DID_NOT_ARRIVE)
        }
    }

    private fun createRecovery(status: SessionVerifiedStatus) = RecoveryFromOtherDevice(
        encryptionService = encryptionService,
        sessionVerificationService = FakeSessionVerificationService(initialSessionVerifiedStatus = status),
    )

    private companion object {
        val CEILING = 60.seconds
    }
}
