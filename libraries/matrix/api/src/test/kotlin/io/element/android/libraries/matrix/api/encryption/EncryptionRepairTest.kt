/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EncryptionRepairTest {
    @Test
    fun `already enabled is a no-op`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            }
        )
        service.recoveryStateStateFlow.value = RecoveryState.ENABLED

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.Repaired)
        assertThat(enableCalls).isEqualTo(0)
    }

    @Test
    fun `provisioning counts as repaired only once the state agrees`() = runTest {
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ -> Result.success("key") }
        )
        service.recoveryStateStateFlow.value = RecoveryState.DISABLED
        // What a real, healthy provision looks like: the SDK reports the new state.
        service.recoveryStateStateFlow.value = RecoveryState.ENABLED

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.Repaired)
    }

    @Test
    fun `incomplete never calls enableRecovery, because that rotates the secret store`() = runTest {
        // Recovery::enable always runs create_secret_store, minting a new SSSS key and a new
        // m.secret_storage.default_key. That strands the previous store's cross-signing copies and
        // permanently invalidates any recovery key already saved for this account, including one
        // in the same user's iOS keychain. On a device with no private cross-signing keys it
        // cannot help anyway, so the tap would spend the last silent way back and still end at a
        // reset. Turning backups on is the one non-rotating thing worth trying.
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            }
        )
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.ResetRequired)
        assertThat(enableCalls).isEqualTo(0)
    }

    @Test
    fun `a blocked provision needs a reset, and nothing destructive is attempted`() = runTest {
        // enableRecovery refuses while a backup exists on the server. There is deliberately no
        // disableRecovery escalation: it throws BackupNotEnabled in exactly this situation, and
        // where it would succeed it destroys the last server copy of the cross-signing keys.
        var disableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ -> Result.failure(IllegalStateException("BackupExistsOnServer")) }
        )
        service.givenDisableRecoveryFailure(IllegalStateException("disableRecovery must not be called"))
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.ResetRequired)
        assertThat(disableCalls).isEqualTo(0)
    }

    @Test
    fun `reports not-yet, never a reset, while the state is unreadable`() = runTest {
        // Android pins the recovery flow to WAITING_FOR_SYNC the whole time sync is not Running,
        // so this is what a tap during a network blip looks like. Offering a reset here would
        // march the user through MAS to fix nothing.
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            }
        )
        service.recoveryStateStateFlow.value = RecoveryState.WAITING_FOR_SYNC

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.NotYet)
        assertThat(enableCalls).isEqualTo(0)
    }
}
