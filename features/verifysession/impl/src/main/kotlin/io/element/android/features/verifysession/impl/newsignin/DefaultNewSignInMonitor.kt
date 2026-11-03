/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.lockscreen.api.LockScreenLockState
import io.element.android.features.lockscreen.api.LockScreenService
import io.element.android.features.securebackup.api.IdentityResetPendingStore
import io.element.android.features.securebackup.api.KeyStorageProvisioner
import io.element.android.features.verifysession.api.NewSignInMonitor
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.VerificationFlowState
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultNewSignInMonitor(
    private val matrixClient: MatrixClient,
    private val ownDevicesClient: OwnDevicesClient,
    private val dismissedSignInStore: DismissedSignInStore,
    private val identityResetPendingStore: IdentityResetPendingStore,
    private val keyStorageProvisioner: KeyStorageProvisioner,
    private val appForegroundStateService: AppForegroundStateService,
    private val lockScreenService: LockScreenService,
    private val systemClock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
) : NewSignInMonitor {
    private val sessionVerificationService = matrixClient.sessionVerificationService

    // In memory, so the per-device limits count from the app launch.
    private val nudges = ConcurrentHashMap<DeviceId, NudgeHistory>()
    private val requestsReceivedAtMillis = ConcurrentHashMap<DeviceId, Long>()

    override suspend fun run(isShowingMainScreens: Flow<Boolean>) = coroutineScope {
        val mainScreens = isShowingMainScreens.stateIn(this, SharingStarted.Eagerly, false)
        combine(
            appForegroundStateService.isInForeground,
            sessionVerificationService.sessionVerifiedStatus,
        ) { isInForeground, verifiedStatus -> isInForeground && verifiedStatus.isVerified() }
            .distinctUntilChanged()
            .collectLatest { isActive ->
                if (!isActive) return@collectLatest
                // Leaves the SDK one sync to download the keys of devices that changed while the app was away.
                delay(FIRST_CHECK_DELAY)
                while (true) {
                    checkOnce(mainScreens)
                    delay(CHECK_INTERVAL)
                }
            }
    }

    override fun onVerificationRequestReceived(deviceId: DeviceId) {
        requestsReceivedAtMillis[deviceId] = systemClock.epochMillis()
    }

    private suspend fun checkOnce(isShowingMainScreens: StateFlow<Boolean>) = withContext(dispatchers.io) {
        skipReason(isShowingMainScreens.value)?.let {
            Timber.d("New sign-in check skipped: $it")
            return@withContext
        }
        val devices = ownDevicesClient.devices().getOrElse {
            Timber.w("New sign-in check skipped: could not read the devices (${it.describe()})")
            return@withContext
        }
        val keys = ownDevicesClient.deviceKeys().getOrElse {
            Timber.w("New sign-in check skipped: could not read the device keys (${it.describe()})")
            return@withContext
        }
        val candidate = selectNudgeCandidate(
            ownDeviceId = matrixClient.deviceId,
            devices = devices,
            keys = keys,
            nowMillis = systemClock.epochMillis(),
            dismissed = dismissedSignInStore.dismissedDevices(),
            nudges = nudges.toMap(),
            requestsReceivedAtMillis = requestsReceivedAtMillis.toMap(),
        ) ?: return@withContext
        // The reads take time; anything that started meanwhile wins over the nudge.
        skipReason(isShowingMainScreens.value)?.let {
            Timber.d("Nudge to ${candidate.value} skipped: $it")
            return@withContext
        }
        ownDevicesClient.sendRetryRequest(candidate)
            .onSuccess {
                val nowMillis = systemClock.epochMillis()
                nudges.compute(candidate) { _, previous -> NudgeHistory(count = (previous?.count ?: 0) + 1, lastAtMillis = nowMillis) }
                Timber.i("Asked device ${candidate.value} to send its verification request again")
            }
            .onFailure {
                Timber.w("Could not nudge device ${candidate.value} (${it.describe()})")
            }
    }

    private suspend fun skipReason(isShowingMainScreens: Boolean): String? = when {
        !appForegroundStateService.isInForeground.value -> "app in the background"
        !sessionVerificationService.sessionVerifiedStatus.value.isVerified() -> "this device is not verified"
        identityResetPendingStore.isPending() || matrixClient.identityResetGuard.isHeld.value || keyStorageProvisioner.isProvisioning.value ->
            "identity reset in progress"
        sessionVerificationService.verificationFlowState.value.isInProgress() -> "verification in progress"
        !isShowingMainScreens -> "another flow is shown"
        lockScreenService.lockState.value != LockScreenLockState.Unlocked -> "app locked"
        appForegroundStateService.isInCall.value || appForegroundStateService.hasRingingCall.value -> "call in progress"
        !matrixClient.encryptionService.awaitE2eeInitialization(E2EE_INITIALIZATION_WAIT) -> "encryption not initialised"
        // Only a device holding the cross-signing keys can sign the new one.
        matrixClient.encryptionService.recoveryState() != RecoveryState.ENABLED -> "recovery not enabled"
        else -> null
    }

    private fun VerificationFlowState.isInProgress() = when (this) {
        VerificationFlowState.DidAcceptVerificationRequest,
        VerificationFlowState.DidStartSasVerification,
        is VerificationFlowState.DidReceiveVerificationData -> true
        VerificationFlowState.Initial,
        VerificationFlowState.DidFinish,
        VerificationFlowState.DidCancel,
        VerificationFlowState.DidFail -> false
    }

    private fun Throwable.describe() = (this as? OwnDevicesRequestException)?.message ?: javaClass.simpleName

    companion object {
        val FIRST_CHECK_DELAY = 5.seconds
        val CHECK_INTERVAL = 60.seconds
        private val E2EE_INITIALIZATION_WAIT = 5.seconds
    }
}
