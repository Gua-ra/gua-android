/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    fun `a failing enableBackups still reaches a verdict, it does not swallow the tap`() = runTest {
        // enableBackups fails on exactly the accounts this repairs, because a backup version
        // already exists on the server. Treating that as "try again later" returned NotYet, which
        // navigates nowhere and says nothing, so the button appeared to do nothing at all.
        val service = FakeEncryptionService()
        service.givenEnableBackupsFailure(IllegalStateException("BackupExistsOnServer"))
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.ResetRequired)
    }

    @Test
    fun `a failed call is answered immediately, it does not sit out the confirmation wait`() = runTest {
        // Recovery only reaches ENABLED off the back of a call that worked, so waiting after one
        // that failed is dead time: the user watches a spinner and gets the same answer. This is
        // the difference between a tap that feels instant and one that feels broken.
        val service = FakeEncryptionService()
        service.givenEnableBackupsFailure(IllegalStateException("BackupExistsOnServer"))
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE

        val before = testScheduler.currentTime
        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.ResetRequired)

        // The confirmation wait is two seconds. Anything near it means the failure path is sitting
        // it out; the only time that should pass here is the call itself.
        assertThat(testScheduler.currentTime - before).isLessThan(100)
    }

    @Test
    fun `a failure still honours a state something else already repaired`() = runTest {
        // Not waiting is not the same as not looking. If a concurrent path enabled recovery, the
        // current value says so and the user should not be sent to a reset they do not need.
        val service = FakeEncryptionService()
        service.givenEnableBackupsFailure(IllegalStateException("BackupExistsOnServer"))
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE

        // Flipped while the call is in flight, so the branch under test is genuinely the one that
        // runs: the repair has already read INCOMPLETE and is on the failure path.
        service.givenEnableBackupsSideEffect {
            service.recoveryStateStateFlow.value = RecoveryState.ENABLED
        }

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.Repaired)
    }

    @Test
    fun `after a reset, a successful mint the SDK confirms is repaired`() = runTest {
        var enableCalls = 0
        var sdkState = RecoveryState.DISABLED
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                sdkState = RecoveryState.ENABLED
                Result.success("key")
            },
        )
        service.recoveryStateLambda = { sdkState }

        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.Repaired)
        assertThat(enableCalls).isEqualTo(1)
    }

    @Test
    fun `after a reset, the verdict is the SDK's own read, not the sync-conditioned flow`() = runTest {
        var enableCalls = 0
        var sdkState = RecoveryState.DISABLED
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                sdkState = RecoveryState.ENABLED
                Result.success("key")
            },
        )
        service.recoveryStateStateFlow.value = RecoveryState.WAITING_FOR_SYNC
        service.recoveryStateLambda = { sdkState }

        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.Repaired)
        assertThat(enableCalls).isEqualTo(1)
    }

    @Test
    fun `after a reset, a store that exported nothing ends the loop after exactly one mint`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            }
        )
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE

        val before = testScheduler.currentTime
        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.IdentityIncompleteAfterReset)
        assertThat(enableCalls).isEqualTo(1)
        assertThat(testScheduler.currentTime - before).isLessThan(1_000)
    }

    @Test
    fun `after a reset, an unknown state receives one bounded re-read and no more`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            }
        )
        service.recoveryStateStateFlow.value = RecoveryState.UNKNOWN
        service.recoveryStateLambda = { RecoveryState.UNKNOWN }

        val before = testScheduler.currentTime
        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.IdentityIncompleteAfterReset)
        assertThat(enableCalls).isEqualTo(1)
        val waited = testScheduler.currentTime - before
        assertThat(waited).isAtLeast(2_000)
        assertThat(waited).isLessThan(3_000)
    }

    @Test
    fun `after a reset, an unknown state that settles within the re-read is repaired`() = runTest {
        var sdkState = RecoveryState.UNKNOWN
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ -> Result.success("key") }
        )
        service.recoveryStateStateFlow.value = RecoveryState.UNKNOWN
        service.recoveryStateLambda = { sdkState }
        backgroundScope.launch {
            delay(500)
            sdkState = RecoveryState.ENABLED
            service.recoveryStateStateFlow.value = RecoveryState.ENABLED
        }

        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.Repaired)
    }

    @Test
    fun `after a reset, an attempt that threw before minting is retried`() = runTest {
        var enableCalls = 0
        var sdkState = RecoveryState.DISABLED
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                if (enableCalls == 1) {
                    Result.failure(IllegalStateException("network"))
                } else {
                    sdkState = RecoveryState.ENABLED
                    Result.success("key")
                }
            },
        )
        service.recoveryStateLambda = { sdkState }

        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.Repaired)
        assertThat(enableCalls).isEqualTo(2)
    }

    @Test
    fun `after a reset, a backup that already exists on the server ends the loop at once`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.failure(RecoveryException.BackupExistsOnServer)
            },
        )
        service.recoveryStateLambda = { RecoveryState.INCOMPLETE }

        val before = testScheduler.currentTime
        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.ResetRequired)
        assertThat(enableCalls).isEqualTo(1)
        assertThat(testScheduler.currentTime - before).isLessThan(100)
    }

    @Test
    fun `after a reset, a mint the SDK reads as DISABLED ends the loop without another rotation`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            },
        )
        service.recoveryStateLambda = { RecoveryState.DISABLED }

        val before = testScheduler.currentTime
        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.IdentityIncompleteAfterReset)
        assertThat(enableCalls).isEqualTo(1)
        assertThat(testScheduler.currentTime - before).isLessThan(1_000)
    }

    @Test
    fun `after a reset, attempts that keep throwing exhaust the backoff and end at a reset`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.failure(IllegalStateException("network"))
            },
        )
        service.recoveryStateLambda = { RecoveryState.DISABLED }

        val before = testScheduler.currentTime
        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.ResetRequired)
        assertThat(enableCalls).isEqualTo(5)
        val waited = testScheduler.currentTime - before
        assertThat(waited).isAtLeast(58_000)
        assertThat(waited).isLessThan(60_000)
    }

    @Test
    fun `after a reset, a mint that failed only after the SDK reported it done is still decisive`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.failure(RecoveryException.MintedButUnconfirmed(IllegalStateException("network")))
            },
        )
        service.recoveryStateLambda = { RecoveryState.INCOMPLETE }

        assertThat(service.provisionAfterReset()).isEqualTo(EncryptionRepairOutcome.IdentityIncompleteAfterReset)
        assertThat(enableCalls).isEqualTo(1)
    }

    @Test
    fun `a banner provision whose store the SDK cannot read is a failure, not a reset`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            },
        )
        service.recoveryStateStateFlow.value = RecoveryState.DISABLED
        service.recoveryStateLambda = { RecoveryState.UNKNOWN }

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.Failed)
        assertThat(enableCalls).isEqualTo(1)
    }

    @Test
    fun `provisioning refuses while the encryption initialisation is still pending`() = runTest {
        var enableCalls = 0
        val service = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            },
            awaitE2eeInitializationLambda = { false },
        )
        service.recoveryStateStateFlow.value = RecoveryState.DISABLED

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.NotYet)
        assertThat(enableCalls).isEqualTo(0)
    }

    @Test
    fun `a banner repair is judged on the SDK's own read`() = runTest {
        val service = FakeEncryptionService()
        service.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE
        var sdkState = RecoveryState.INCOMPLETE
        service.recoveryStateLambda = { sdkState }
        service.givenEnableBackupsSideEffect { sdkState = RecoveryState.ENABLED }

        assertThat(service.repairWithoutReset()).isEqualTo(EncryptionRepairOutcome.Repaired)
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
