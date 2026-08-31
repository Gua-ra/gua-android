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
    fun `enableRecovery succeeding is not enough on its own`() = runTest {
        // THE BUG THIS EXISTS TO CATCH. On a device holding no private cross-signing keys,
        // enableRecovery SUCCEEDS: it mints a new secret store, exports nothing useful into it,
        // and the account drops straight back to INCOMPLETE. Trusting the return value reported
        // success, the caller navigated nowhere, and the banner never cleared, which is precisely
        // what "the finish setup button does nothing" looked like.
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            }
        )
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.ResetRequired)
        assertThat(enableCalls).isEqualTo(1)
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
