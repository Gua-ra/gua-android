/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl.state

import android.Manifest
import android.os.Build
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.ftue.api.state.FtueService
import io.element.android.features.ftue.api.state.FtueState
import io.element.android.features.lockscreen.api.LockScreenService
import io.element.android.libraries.core.coroutine.mapState
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.permissions.api.PermissionStateProvider
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.toolbox.api.sdk.BuildVersionSdkIntProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultFtueService(
    private val sdkVersionProvider: BuildVersionSdkIntProvider,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val analyticsService: AnalyticsService,
    private val permissionStateProvider: PermissionStateProvider,
    private val lockScreenService: LockScreenService,
    private val sessionVerificationService: SessionVerificationService,
) : FtueService {
    private val userNeedsToConfirmSessionVerificationSuccess = MutableStateFlow(false)

    val ftueStepStateFlow = MutableStateFlow<InternalFtueState>(InternalFtueState.Unknown)

    override val state = ftueStepStateFlow
        .mapState {
            when (it) {
                is InternalFtueState.Unknown -> FtueState.Unknown
                is InternalFtueState.Incomplete -> FtueState.Incomplete
                is InternalFtueState.Complete -> FtueState.Complete
            }
        }

    init {
        combine(
            // Gua: encryption is set up on first sign-in and restored on re-login entirely in the
            // background (see SilentSessionEncryptionBootstrapper in the matrix impl module), so we
            // never gate the user on the identity-confirmation / verify ceremony. We therefore do
            // NOT flip userNeedsToConfirmSessionVerificationSuccess when the session is not verified;
            // the FtueStep.SessionVerification step is unreachable below. This mirrors iOS
            // OnboardingFlowCoordinator.requiresVerification == false exactly.
            sessionVerificationService.sessionVerifiedStatus,
            userNeedsToConfirmSessionVerificationSuccess,
            analyticsService.didAskUserConsentFlow.distinctUntilChanged(),
        ) { _, _, _ ->
            updateFtueStep()
        }
            .launchIn(sessionCoroutineScope)
    }

    fun updateFtueStep() = sessionCoroutineScope.launch {
        val step = getNextStep(null)
        ftueStepStateFlow.value = when (step) {
            null -> InternalFtueState.Complete
            else -> InternalFtueState.Incomplete(step)
        }
    }

    private suspend fun getNextStep(completedStep: FtueStep? = null): FtueStep? =
        when (completedStep) {
            // GUA FORK: never wait for the verification state. The only step that needed it is
            // the session-verification ceremony below, which this fork does not present, and the
            // wait held a device whose identity was reset from ANOTHER device on a blank screen
            // for good (the SDK's encryption set-up never reports ready there). The room list
            // handles an unverified or incomplete device with its setup banner instead.
            null -> getNextStep(FtueStep.WaitingForInitialState)
            // Gua: never gate onboarding on session verification. Encryption is bootstrapped /
            // restored silently in the background, mirroring iOS requiresVerification == false, so
            // the ChooseSelfVerificationMode ceremony is never presented. Device verification /
            // recovery remains reachable from Settings (see SecureBackup / PreferencesRootPresenter).
            FtueStep.WaitingForInitialState -> getNextStep(FtueStep.SessionVerification)
            FtueStep.SessionVerification -> if (shouldAskNotificationPermissions()) {
                FtueStep.NotificationsOptIn
            } else {
                getNextStep(FtueStep.NotificationsOptIn)
            }
            FtueStep.NotificationsOptIn -> if (shouldDisplayLockscreenSetup()) {
                FtueStep.LockscreenSetup
            } else {
                getNextStep(FtueStep.LockscreenSetup)
            }
            FtueStep.LockscreenSetup -> if (needsAnalyticsOptIn()) {
                FtueStep.AnalyticsOptIn
            } else {
                getNextStep(FtueStep.AnalyticsOptIn)
            }
            FtueStep.AnalyticsOptIn -> null
        }

    private suspend fun needsAnalyticsOptIn(): Boolean {
        return analyticsService.didAskUserConsentFlow.first().not()
    }

    private suspend fun shouldAskNotificationPermissions(): Boolean {
        return if (sdkVersionProvider.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val isPermissionDenied = permissionStateProvider.isPermissionDenied(permission).first()
            val isPermissionGranted = permissionStateProvider.isPermissionGranted(permission)
            !isPermissionGranted && !isPermissionDenied
        } else {
            false
        }
    }

    private suspend fun shouldDisplayLockscreenSetup(): Boolean {
        return lockScreenService.isSetupRequired().first()
    }

    fun onUserCompletedSessionVerification() {
        userNeedsToConfirmSessionVerificationSuccess.value = false
    }
}

sealed interface FtueStep {
    data object WaitingForInitialState : FtueStep
    data object SessionVerification : FtueStep
    data object NotificationsOptIn : FtueStep
    data object AnalyticsOptIn : FtueStep
    data object LockscreenSetup : FtueStep
}
