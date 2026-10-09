/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.encryption.RecoveryException
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiClient
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiSyncService
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiTaskHandle
import io.element.android.libraries.matrix.impl.sync.RustSyncService
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.matrix.rustcomponents.sdk.BackupState
import org.matrix.rustcomponents.sdk.BackupStateListener
import org.matrix.rustcomponents.sdk.EnableRecoveryProgressListener
import org.matrix.rustcomponents.sdk.Encryption
import org.matrix.rustcomponents.sdk.NoHandle
import org.matrix.rustcomponents.sdk.RecoveryStateListener
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.VerificationStateListener
import kotlin.time.Duration.Companion.seconds
import org.matrix.rustcomponents.sdk.EnableRecoveryProgress as RustEnableRecoveryProgress
import org.matrix.rustcomponents.sdk.RecoveryState as RustRecoveryState

@OptIn(ExperimentalCoroutinesApi::class)
class RustEncryptionServiceTest {
    private open class GatedFfiEncryption : Encryption(NoHandle) {
        val initialization = CompletableDeferred<Unit>()
        var waitCalls = 0
        var recoveryState = RustRecoveryState.UNKNOWN

        override suspend fun waitForE2eeInitializationTasks() {
            waitCalls++
            initialization.await()
        }

        override fun verificationStateListener(listener: VerificationStateListener): TaskHandle = FakeFfiTaskHandle()
        override fun recoveryStateListener(listener: RecoveryStateListener): TaskHandle = FakeFfiTaskHandle()
        override fun backupStateListener(listener: BackupStateListener): TaskHandle = FakeFfiTaskHandle()
        override fun backupState(): BackupState = BackupState.UNKNOWN
        override fun recoveryState(): RustRecoveryState = recoveryState
        override suspend fun isLastDevice(): Boolean = false
        override suspend fun hasDevicesToVerifyAgainst(): Boolean = false
    }

    @Test
    fun `every caller shares the one SDK wait`() = runTest {
        val ffi = GatedFfiEncryption()
        val sut = createService(ffi)

        val first = async { sut.awaitE2eeInitialization() }
        val second = async { sut.awaitE2eeInitialization() }
        runCurrent()

        assertThat(ffi.waitCalls).isEqualTo(1)
        assertThat(first.isCompleted).isFalse()

        ffi.initialization.complete(Unit)
        assertThat(first.await()).isTrue()
        assertThat(second.await()).isTrue()
        assertThat(sut.awaitE2eeInitialization()).isTrue()
        assertThat(ffi.waitCalls).isEqualTo(1)
    }

    @Test
    fun `the bound is a refusal, not a go-ahead`() = runTest {
        val ffi = GatedFfiEncryption()
        val sut = createService(ffi)

        assertThat(sut.awaitE2eeInitialization(timeout = 1.seconds)).isFalse()

        ffi.initialization.complete(Unit)
        assertThat(sut.awaitE2eeInitialization(timeout = 1.seconds)).isTrue()
    }

    @Test
    fun `every backup creator is refused until the initialisation has finished`() = runTest {
        val ffi = GatedFfiEncryption()
        val sut = createService(ffi)

        assertThat(sut.enableRecovery(waitForBackupsToUpload = false).exceptionOrNull()).isEqualTo(RecoveryException.E2eeInitializationPending)
        assertThat(sut.enableBackups().exceptionOrNull()).isEqualTo(RecoveryException.E2eeInitializationPending)
        assertThat(sut.resetRecoveryKey().exceptionOrNull()).isEqualTo(RecoveryException.E2eeInitializationPending)
        assertThat(sut.recover("key").exceptionOrNull()).isEqualTo(RecoveryException.E2eeInitializationPending)
    }

    @Test
    fun `a join cancelled with the session is a refusal, not a go-ahead`() = runTest {
        val ffi = GatedFfiEncryption()
        val session = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val sut = createService(ffi, sessionCoroutineScope = session)
        runCurrent()

        session.cancel()

        assertThat(sut.awaitE2eeInitialization(timeout = 1.seconds)).isFalse()
        assertThat(sut.enableRecovery(waitForBackupsToUpload = false).exceptionOrNull()).isEqualTo(RecoveryException.E2eeInitializationPending)
    }

    @Test
    fun `a failure after the SDK reported the mint done is surfaced as minted but unconfirmed`() = runTest {
        val ffi = object : GatedFfiEncryption() {
            override suspend fun enableRecovery(
                waitForBackupsToUpload: Boolean,
                passphrase: String?,
                progressListener: EnableRecoveryProgressListener,
            ): String {
                progressListener.onUpdate(RustEnableRecoveryProgress.Done(recoveryKey = "key"))
                error("recomputing the recovery state failed")
            }
        }
        ffi.initialization.complete(Unit)
        val sut = createService(ffi)

        val result = sut.enableRecovery(waitForBackupsToUpload = false)

        assertThat(result.exceptionOrNull()).isInstanceOf(RecoveryException.MintedButUnconfirmed::class.java)
    }

    @Test
    fun `a failure before the mint is reported as it is`() = runTest {
        val ffi = object : GatedFfiEncryption() {
            override suspend fun enableRecovery(
                waitForBackupsToUpload: Boolean,
                passphrase: String?,
                progressListener: EnableRecoveryProgressListener,
            ): String {
                progressListener.onUpdate(RustEnableRecoveryProgress.CreatingBackup)
                error("network")
            }
        }
        ffi.initialization.complete(Unit)
        val sut = createService(ffi)

        val result = sut.enableRecovery(waitForBackupsToUpload = false)

        assertThat(result.exceptionOrNull()).isInstanceOf(RecoveryException.Client::class.java)
    }

    @Test
    fun `concurrent recovery writes reach the SDK one at a time`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var inFlight = 0
        var maxInFlight = 0
        val ffi = object : GatedFfiEncryption() {
            override suspend fun enableRecovery(
                waitForBackupsToUpload: Boolean,
                passphrase: String?,
                progressListener: EnableRecoveryProgressListener,
            ): String {
                calls++
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                gate.await()
                inFlight--
                return "key"
            }

            override suspend fun resetRecoveryKey(): String {
                calls++
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                inFlight--
                return "key"
            }
        }
        ffi.initialization.complete(Unit)
        val sut = createService(ffi)

        val bootstrap = async { sut.enableRecovery(waitForBackupsToUpload = false) }
        val bannerTap = async { sut.enableRecovery(waitForBackupsToUpload = false) }
        val reset = async { sut.resetRecoveryKey() }
        runCurrent()
        assertThat(calls).isEqualTo(1)

        gate.complete(Unit)
        assertThat(bootstrap.await().isSuccess).isTrue()
        assertThat(bannerTap.await().isSuccess).isTrue()
        assertThat(reset.await().isSuccess).isTrue()
        assertThat(calls).isEqualTo(3)
        assertThat(maxInFlight).isEqualTo(1)
    }

    @Test
    fun `the recovery state is read from the SDK directly`() = runTest {
        val ffi = GatedFfiEncryption().apply { recoveryState = RustRecoveryState.INCOMPLETE }
        val sut = createService(ffi)

        assertThat(sut.recoveryState()).isEqualTo(RecoveryState.INCOMPLETE)
    }

    private fun TestScope.createService(
        ffi: Encryption,
        sessionCoroutineScope: CoroutineScope = backgroundScope,
    ): RustEncryptionService {
        val syncService = RustSyncService(
            inner = FakeFfiSyncService(),
            dispatcher = StandardTestDispatcher(testScheduler),
            sessionCoroutineScope = sessionCoroutineScope,
            identityResetHold = MutableStateFlow(false),
        )
        return RustEncryptionService(
            client = FakeFfiClient(encryption = ffi),
            syncService = syncService,
            sessionCoroutineScope = sessionCoroutineScope,
            dispatchers = testCoroutineDispatchers(),
        )
    }
}
