/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import com.google.common.truth.Truth.assertThat
import io.element.android.features.lockscreen.api.LockScreenLockState
import io.element.android.features.lockscreen.test.FakeLockScreenService
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.api.verification.VerificationFlowState
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.libraries.matrix.test.encryption.FakeIdentityResetGuard
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import io.element.android.services.appnavstate.test.FakeAppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultNewSignInMonitorTest {
    @Test
    fun `the first check runs 5 seconds after start, then every 60 seconds`() = runTest {
        val harness = Harness(this)
        harness.start()

        advance(5.seconds - 1.milliseconds)
        assertThat(harness.client.devicesCallCount).isEqualTo(0)
        advance(1.milliseconds)
        assertThat(harness.client.devicesCallCount).isEqualTo(1)
        assertThat(harness.client.sendAttempts).containsExactly(NEW_DEVICE)

        advance(60.seconds)
        assertThat(harness.client.devicesCallCount).isEqualTo(2)
        advance(60.seconds)
        assertThat(harness.client.devicesCallCount).isEqualTo(3)
    }

    @Test
    fun `nothing runs in the background, and a check runs 5 seconds after the app comes back`() = runTest {
        val harness = Harness(this)
        harness.foreground.givenIsInForeground(false)
        harness.start()

        advance(10.minutes)
        assertThat(harness.client.devicesCallCount).isEqualTo(0)

        harness.foreground.givenIsInForeground(true)
        advance(5.seconds)
        assertThat(harness.client.devicesCallCount).isEqualTo(1)

        harness.foreground.givenIsInForeground(false)
        advance(10.minutes)
        assertThat(harness.client.devicesCallCount).isEqualTo(1)
    }

    @Test
    fun `a check runs 5 seconds after this device becomes verified`() = runTest {
        val harness = Harness(this, verifiedStatus = SessionVerifiedStatus.Unknown)
        harness.start()

        advance(2.minutes)
        assertThat(harness.client.devicesCallCount).isEqualTo(0)

        harness.sessionVerificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        advance(5.seconds)
        assertThat(harness.client.devicesCallCount).isEqualTo(1)
        assertThat(harness.client.sendAttempts).containsExactly(NEW_DEVICE)
    }

    @Test
    fun `a device is nudged at most once per 120 seconds and 3 times in all`() = runTest {
        val harness = Harness(this)
        harness.start()

        advance(5.seconds)
        assertThat(harness.client.sendAttempts).hasSize(1)
        advance(60.seconds)
        assertThat(harness.client.sendAttempts).hasSize(1)
        advance(60.seconds)
        assertThat(harness.client.sendAttempts).hasSize(2)
        advance(2.minutes)
        assertThat(harness.client.sendAttempts).hasSize(3)
        advance(10.minutes)
        assertThat(harness.client.sendAttempts).containsExactly(NEW_DEVICE, NEW_DEVICE, NEW_DEVICE)
    }

    @Test
    fun `a 401 skips the round without signing out, and the next round goes ahead`() = runTest {
        val harness = Harness(this)
        val workingDevices = harness.client.devicesResult
        harness.client.devicesResult = { Result.failure(OwnDevicesRequestException(401)) }
        harness.start()

        advance(5.seconds)
        assertThat(harness.client.devicesCallCount).isEqualTo(1)
        assertThat(harness.client.sendAttempts).isEmpty()

        harness.client.devicesResult = workingDevices
        advance(60.seconds)
        assertThat(harness.client.sendAttempts).containsExactly(NEW_DEVICE)
    }

    @Test
    fun `a failed nudge does not count against the device`() = runTest {
        val harness = Harness(this)
        harness.client.sendRetryRequestResult = { Result.failure(OwnDevicesRequestException(401)) }
        harness.start()

        advance(5.seconds)
        harness.client.sendRetryRequestResult = { Result.success(Unit) }
        advance(60.seconds)
        advance(60.seconds)
        // Attempt at 5 s failed, so 65 s is not inside a cooldown; 125 s is.
        assertThat(harness.client.sendAttempts).hasSize(2)
    }

    @Test
    fun `a device that sent this device a request in the last 10 minutes is not nudged`() = runTest {
        val harness = Harness(this)
        harness.monitor.onVerificationRequestReceived(NEW_DEVICE)
        harness.start()

        advance(5.seconds)
        assertThat(harness.client.devicesCallCount).isEqualTo(1)
        assertThat(harness.client.sendAttempts).isEmpty()

        advance(10.minutes)
        assertThat(harness.client.sendAttempts).containsExactly(NEW_DEVICE)
    }

    @Test
    fun `a dismissed device is not nudged`() = runTest {
        val harness = Harness(this)
        harness.dismissedStore.dismiss(NEW_DEVICE)
        harness.start()

        advance(5.minutes)
        assertThat(harness.client.devicesCallCount).isGreaterThan(0)
        assertThat(harness.client.sendAttempts).isEmpty()
    }

    @Test
    fun `no round while recovery is not enabled`() = assertSkipped { recoveryState = RecoveryState.INCOMPLETE }

    @Test
    fun `no round while the recovery state is unknown`() = assertSkipped { recoveryState = RecoveryState.UNKNOWN }

    @Test
    fun `no round while the encryption is not initialised`() = assertSkipped { encryptionService.awaitE2eeInitializationLambda = { false } }

    @Test
    fun `no round while an identity reset is pending`() = assertSkipped { resetPendingStore.pending = true }

    @Test
    fun `no round while an identity reset is running`() = assertSkipped { identityResetGuard.isHeld.value = true }

    @Test
    fun `no round while key storage is being provisioned`() = assertSkipped { keyStorageProvisioner.isProvisioning.value = true }

    @Test
    fun `no round while a verification is in progress`() = assertSkipped {
        sessionVerificationService.emitVerificationFlowState(VerificationFlowState.DidStartSasVerification)
    }

    @Test
    fun `no round while another flow covers the main screens`() = assertSkipped { isShowingMainScreens.value = false }

    @Test
    fun `no round while the app is locked`() = assertSkipped { lockScreenService.setLockState(LockScreenLockState.Locked) }

    @Test
    fun `no round during a call`() = assertSkipped { foreground.updateIsInCallState(true) }

    @Test
    fun `no round while a call is ringing`() = assertSkipped { foreground.updateHasRingingCall(true) }

    @Test
    fun `a finished verification does not block the next round`() = runTest {
        val harness = Harness(this)
        harness.sessionVerificationService.emitVerificationFlowState(VerificationFlowState.DidFinish)
        harness.start()

        advance(5.seconds)
        assertThat(harness.client.sendAttempts).containsExactly(NEW_DEVICE)
    }

    @Test
    fun `an identity reset that starts during the reads stops the nudge`() = runTest {
        val harness = Harness(this)
        val keys = harness.client.deviceKeysResult
        harness.client.deviceKeysResult = {
            harness.resetPendingStore.pending = true
            keys()
        }
        harness.start()

        advance(5.seconds)
        assertThat(harness.client.devicesCallCount).isEqualTo(1)
        assertThat(harness.client.sendAttempts).isEmpty()
    }

    private fun assertSkipped(closeGate: suspend Harness.() -> Unit) = runTest {
        val harness = Harness(this)
        harness.closeGate()
        harness.start()

        advance(5.minutes)
        assertThat(harness.client.devicesCallCount).isEqualTo(0)
        assertThat(harness.client.sendAttempts).isEmpty()
    }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private class Harness(
        private val scope: TestScope,
        verifiedStatus: SessionVerifiedStatus = SessionVerifiedStatus.Verified,
    ) {
        val clock = SystemClock { START_MILLIS + scope.testScheduler.currentTime }
        val sessionVerificationService = FakeSessionVerificationService(initialSessionVerifiedStatus = verifiedStatus)
        var recoveryState = RecoveryState.ENABLED
        val encryptionService = FakeEncryptionService(recoveryStateLambda = { recoveryState })
        val identityResetGuard = FakeIdentityResetGuard()
        val client = FakeOwnDevicesClient(
            devicesResult = {
                Result.success(listOf(OwnDevice(OWN_DEVICE, clock.epochMillis()), OwnDevice(NEW_DEVICE, clock.epochMillis() - 60_000)))
            },
            deviceKeysResult = {
                Result.success(
                    OwnDeviceKeys(
                        selfSigningKeyIds = setOf(SSK_ID),
                        devices = mapOf(
                            OWN_DEVICE to OwnDeviceKeyInfo(ownSignatureKeyIds = setOf(SSK_ID), isDehydrated = false),
                            NEW_DEVICE to OwnDeviceKeyInfo(ownSignatureKeyIds = emptySet(), isDehydrated = false),
                        ),
                    )
                )
            },
        )
        val dismissedStore = InMemoryDismissedSignInStore()
        val resetPendingStore = FakeIdentityResetPendingStore()
        val keyStorageProvisioner = FakeKeyStorageProvisioner()
        val foreground = FakeAppForegroundStateService()
        val lockScreenService = FakeLockScreenService().apply { setLockState(LockScreenLockState.Unlocked) }
        val isShowingMainScreens = MutableStateFlow(true)

        val monitor = DefaultNewSignInMonitor(
            matrixClient = FakeMatrixClient(
                deviceId = OWN_DEVICE,
                sessionVerificationService = sessionVerificationService,
                encryptionService = encryptionService,
                identityResetGuard = identityResetGuard,
            ),
            ownDevicesClient = client,
            dismissedSignInStore = dismissedStore,
            identityResetPendingStore = resetPendingStore,
            keyStorageProvisioner = keyStorageProvisioner,
            appForegroundStateService = foreground,
            lockScreenService = lockScreenService,
            systemClock = clock,
            dispatchers = scope.testCoroutineDispatchers(),
        )

        fun start() {
            scope.backgroundScope.launch { monitor.run(isShowingMainScreens) }
            scope.runCurrent()
        }
    }

    private companion object {
        const val START_MILLIS = 1_800_000_000_000L
        const val SSK_ID = "ed25519:ssk-public-key"
        val OWN_DEVICE = DeviceId("OWNDEVICE")
        val NEW_DEVICE = DeviceId("NEWDEVICE")
    }
}
