/*
 * Copyright Gua
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
        val service = FakeEncryptionService(enableRecoveryLambda = { _, _ -> enableCalls++; Result.success("key") })
        service.recoveryStateStateFlow.value = RecoveryState.ENABLED

        assertThat(service.repairWithoutReset().isSuccess).isTrue()
        assertThat(enableCalls).isEqualTo(0)
    }

    @Test
    fun `disabled provisions key storage`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(enableRecoveryLambda = { _, _ -> enableCalls++; Result.success("key") })
        service.recoveryStateStateFlow.value = RecoveryState.DISABLED

        assertThat(service.repairWithoutReset().isSuccess).isTrue()
        assertThat(enableCalls).isEqualTo(1)
    }

    @Test
    fun `incomplete finishes provisioning when nothing hands the secrets over`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(enableRecoveryLambda = { _, _ -> enableCalls++; Result.success("key") })
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE

        assertThat(service.repairWithoutReset().isSuccess).isTrue()
        assertThat(enableCalls).isEqualTo(1)
    }

    @Test
    fun `incomplete fails rather than resetting when a backup blocks provisioning`() = runTest {
        // enableRecovery is what refuses when a key backup already exists on the server. The repair
        // must surface that instead of destroying anything, so the caller can disclose the reset.
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ -> Result.failure(IllegalStateException("BackupExistsOnServer")) }
        )
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE

        assertThat(service.repairWithoutReset().isFailure).isTrue()
    }

    @Test
    fun `refuses to act while the state is still unknown`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(enableRecoveryLambda = { _, _ -> enableCalls++; Result.success("key") })
        service.recoveryStateStateFlow.value = RecoveryState.UNKNOWN

        assertThat(service.repairWithoutReset().isFailure).isTrue()
        assertThat(enableCalls).isEqualTo(0)
    }
}
