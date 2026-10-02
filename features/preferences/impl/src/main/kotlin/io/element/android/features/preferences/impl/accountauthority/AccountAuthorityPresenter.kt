/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.authority.AccountAuthorityManager
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityDeviceLabel
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityFingerprint
import io.element.android.libraries.guaresolver.authority.AuthorityPurpose
import io.element.android.libraries.guaresolver.authority.AuthorityRecord
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.guaresolver.authority.InvalidAuthorityRecordException
import io.element.android.libraries.guaresolver.authority.SecurityNotificationView
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.launch

@Inject
class AccountAuthorityPresenter(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val featureFlagService: FeatureFlagService,
    private val authorityManager: AccountAuthorityManager,
    private val identityServiceClient: IdentityServiceClient,
) : Presenter<AccountAuthorityState> {
    @Composable
    @Suppress("LongMethod")
    override fun present(): AccountAuthorityState {
        val coroutineScope = rememberCoroutineScope()

        var featureEnabled by remember { mutableStateOf(false) }
        var phase by remember { mutableStateOf(AccountAuthorityPhase.Loading) }
        var chain by remember { mutableStateOf<AuthorityChainState?>(null) }
        var unavailable by remember { mutableStateOf(false) }
        var deviceHoldsAuthority by remember { mutableStateOf(false) }
        var thisDeviceKey by remember { mutableStateOf<String?>(null) }
        var recoveryArtifact by remember { mutableStateOf<String?>(null) }
        var artifactConfirmed by remember { mutableStateOf(false) }
        var recoveryArtifactInput by remember { mutableStateOf("") }
        var recoveryArtifactError by remember { mutableStateOf<Int?>(null) }
        var candidates by remember { mutableStateOf<List<AuthorityCandidate>>(emptyList()) }
        var selectedCandidate by remember { mutableStateOf<AuthorityCandidate?>(null) }
        var fingerprintConfirmed by remember { mutableStateOf(false) }
        var thisDeviceFingerprint by remember { mutableStateOf<String?>(null) }
        var revocationTarget by remember { mutableStateOf<AuthorityRevocationTarget?>(null) }
        var stepUp by remember { mutableStateOf<AccountAuthorityStepUp?>(null) }
        var stepUpBlock by remember { mutableStateOf<AccountAuthorityStepUpBlock?>(null) }
        var stepUpMethod by remember { mutableStateOf<AccountAuthorityStepUpMethod?>(null) }
        var webStepUpUrl by remember { mutableStateOf<String?>(null) }
        var awaitingWebStepUp by remember { mutableStateOf(false) }
        // Set when the app is paused for the sheet, so the resume right after handing over the URL submits nothing.
        var leftForWebStepUp by remember { mutableStateOf(false) }
        var recoveryRoute by remember { mutableStateOf<AccountAuthorityRecoveryRoute?>(null) }
        var accountRecoveryAcknowledged by remember { mutableStateOf(false) }
        var pin by remember { mutableStateOf("") }
        var approvals by remember { mutableStateOf<List<AuthorityApproval>>(emptyList()) }
        var securityNotifications by remember { mutableStateOf<List<SecurityNotificationView>>(emptyList()) }
        var thisInstallationId by remember { mutableStateOf<String?>(null) }
        var notificationChannelAvailable by remember { mutableStateOf(false) }
        var notificationRemovalTarget by remember { mutableStateOf<SecurityNotificationView?>(null) }
        var errorMessage by remember { mutableStateOf<Int?>(null) }
        var successMessage by remember { mutableStateOf<Int?>(null) }

        suspend fun load() {
            val enabled = featureFlagService.isFeatureEnabled(FeatureFlags.AccountAuthority)
            featureEnabled = enabled
            if (!enabled) {
                phase = AccountAuthorityPhase.Overview
                return
            }
            deviceHoldsAuthority = authorityManager.holdsAuthority()
            thisDeviceKey = authorityManager.authorityDeviceKeyB64Url()
            val accessToken = accessToken()
            if (accessToken == null) {
                errorMessage = R.string.screen_account_authority_error_generic
                phase = AccountAuthorityPhase.Overview
                return
            }
            authorityManager.state(accessToken)
                .onSuccess { state ->
                    chain = state
                    unavailable = false
                    errorMessage = null
                    approvals = if (deviceHoldsAuthority) {
                        authorityManager.approvals(accessToken).getOrElse { emptyList() }
                    } else {
                        emptyList()
                    }
                    candidates = if (deviceHoldsAuthority) {
                        authorityManager.candidates(accessToken).getOrElse { emptyList() }
                            .filter { it.fingerprint.isNotEmpty() }
                    } else {
                        emptyList()
                    }
                    thisInstallationId = authorityManager.installationId()
                    authorityManager.securityNotifications(accessToken)
                        .onSuccess { rows ->
                            securityNotifications = rows
                            notificationChannelAvailable = true
                        }
                        .onFailure { error ->
                            securityNotifications = emptyList()
                            notificationChannelAvailable = error !is AuthorityError.NotificationsDisabled &&
                                error !is AuthorityError.Disabled
                        }
                }
                .onFailure { error ->
                    unavailable = error is AuthorityError.Disabled || error is AuthorityError.NoAccount
                    chain = null
                    errorMessage = if (unavailable) null else error.toMessageRes()
                }
            phase = AccountAuthorityPhase.Overview
        }

        LaunchedEffect(Unit) { load() }

        suspend fun resolveStepUp(
            kind: AccountAuthorityStepUp,
        ): Pair<AccountAuthorityStepUpMethod?, AccountAuthorityStepUpBlock?> {
            val sheetExists = kind != AccountAuthorityStepUp.Oppose &&
                kind != AccountAuthorityStepUp.RemoveNotification
            val fallback = if (sheetExists) AccountAuthorityStepUpMethod.WebSheet else AccountAuthorityStepUpMethod.Pin
            val accessToken = accessToken() ?: return fallback to null
            val status = identityServiceClient.accountFactorStatus(
                accessToken = accessToken,
                userId = matrixClient.sessionId.value,
            ).getOrNull() ?: return fallback to null
            return when {
                !status.hasStrongFactor -> null to AccountAuthorityStepUpBlock.NoFactorRegistered
                status.passkeyRegistered && sheetExists -> AccountAuthorityStepUpMethod.WebSheet to null
                !status.hasPin -> null to if (kind == AccountAuthorityStepUp.RemoveNotification) {
                    AccountAuthorityStepUpBlock.PasskeyNotUsableForNotificationRemoval
                } else {
                    AccountAuthorityStepUpBlock.PasskeyNotUsableForObjection
                }
                else -> AccountAuthorityStepUpMethod.Pin to null
            }
        }

        fun askForStepUp(kind: AccountAuthorityStepUp) {
            coroutineScope.launch {
                pin = ""
                errorMessage = null
                webStepUpUrl = null
                awaitingWebStepUp = false
                leftForWebStepUp = false
                stepUp = kind
                val (method, block) = resolveStepUp(kind)
                stepUpMethod = method
                stepUpBlock = block
                phase = AccountAuthorityPhase.StepUp
            }
        }

        fun resetFlow() {
            pin = ""
            stepUp = null
            stepUpBlock = null
            stepUpMethod = null
            webStepUpUrl = null
            awaitingWebStepUp = false
            leftForWebStepUp = false
            recoveryRoute = null
            accountRecoveryAcknowledged = false
            errorMessage = null
            recoveryArtifact = null
            artifactConfirmed = false
            recoveryArtifactInput = ""
            recoveryArtifactError = null
            selectedCandidate = null
            fingerprintConfirmed = false
            revocationTarget = null
            notificationRemovalTarget = null
            phase = AccountAuthorityPhase.Overview
        }

        suspend fun submitStepUp(factor: AuthorityStepUp) {
            val accessToken = accessToken() ?: return
            val currentChain = chain ?: return
            val candidate = selectedCandidate
            val target = revocationTarget
            val removalTarget = notificationRemovalTarget
            if (stepUp == AccountAuthorityStepUp.Grant && candidate == null) return
            if (stepUp == AccountAuthorityStepUp.Revoke && target == null) return
            if (stepUp == AccountAuthorityStepUp.RemoveNotification && removalTarget == null) return
            phase = AccountAuthorityPhase.Submitting
            val outcome: Result<Unit> = when (stepUp) {
                AccountAuthorityStepUp.Adopt -> authorityManager.adopt(
                    accessToken = accessToken,
                    chain = currentChain,
                    deviceLabel = AuthorityDeviceLabel.current(),
                    stepUp = factor,
                    artifactConfirmed = artifactConfirmed,
                ).map { }
                AccountAuthorityStepUp.Oppose -> authorityManager.oppose(
                    accessToken = accessToken,
                    recordHash = currentChain.pending?.recordHash,
                    pin = pin,
                )
                AccountAuthorityStepUp.Grant -> {
                    authorityManager.grantDevice(
                        accessToken = accessToken,
                        chain = currentChain,
                        candidate = requireNotNull(candidate),
                        stepUp = factor,
                        fingerprintConfirmed = fingerprintConfirmed,
                    ).map { }
                }
                AccountAuthorityStepUp.Revoke -> {
                    authorityManager.revokeDevice(
                        accessToken = accessToken,
                        chain = currentChain,
                        deviceKeyB64Url = requireNotNull(target).deviceKeyB64Url,
                        reason = if (target?.isThisDevice == true) {
                            AuthorityRecord.REASON_REPLACED
                        } else {
                            AuthorityRecord.REASON_UNSPECIFIED
                        },
                        stepUp = factor,
                    ).map { }
                }
                AccountAuthorityStepUp.Recover -> when (recoveryRoute) {
                    AccountAuthorityRecoveryRoute.AccountRecovery ->
                        authorityManager.recoverThroughAccountRecovery(
                            accessToken = accessToken,
                            chain = currentChain,
                            deviceLabel = AuthorityDeviceLabel.current(),
                            stepUp = factor,
                            artifactConfirmed = artifactConfirmed,
                        ).map { }
                    else -> authorityManager.recoverAuthority(
                        accessToken = accessToken,
                        chain = currentChain,
                        recoveryArtifact = recoveryArtifactInput,
                        deviceLabel = AuthorityDeviceLabel.current(),
                        stepUp = factor,
                        artifactConfirmed = artifactConfirmed,
                    ).map { }
                }
                AccountAuthorityStepUp.RemoveNotification -> authorityManager.removeSecurityNotification(
                    accessToken = accessToken,
                    installationId = requireNotNull(removalTarget).installationId,
                    pin = pin,
                )
                null -> return
            }
            outcome
                .onSuccess {
                    val message = when (stepUp) {
                        AccountAuthorityStepUp.Oppose -> R.string.screen_account_authority_opposed
                        AccountAuthorityStepUp.Revoke -> R.string.screen_account_authority_revoked
                        AccountAuthorityStepUp.RemoveNotification ->
                            R.string.screen_account_authority_alerts_removed
                        else -> R.string.screen_account_authority_submitted
                    }
                    resetFlow()
                    successMessage = message
                    load()
                }
                .onFailure { error ->
                    errorMessage = error.toMessageRes()
                    pin = ""
                    awaitingWebStepUp = false
                    leftForWebStepUp = false
                    phase = AccountAuthorityPhase.StepUp
                }
        }

        suspend fun openWebStepUp() {
            val accessToken = accessToken() ?: return
            val purpose = stepUp?.toPurpose() ?: return
            errorMessage = null
            authorityManager.startWebStepUp(accessToken, purpose)
                .onSuccess { url ->
                    webStepUpUrl = url
                    awaitingWebStepUp = true
                    leftForWebStepUp = false
                }
                .onFailure { error -> errorMessage = error.toMessageRes() }
        }

        var isResumed by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            isResumed = true
            onPauseOrDispose {
                isResumed = false
                if (awaitingWebStepUp) leftForWebStepUp = true
            }
        }
        val resumed = isResumed
        LaunchedEffect(resumed) {
            if (!resumed || !awaitingWebStepUp || !leftForWebStepUp) return@LaunchedEffect
            awaitingWebStepUp = false
            leftForWebStepUp = false
            submitStepUp(AuthorityStepUp.WebSheet)
        }

        fun handleEvent(event: AccountAuthorityEvent) {
            if (!featureEnabled) return
            when (event) {
                AccountAuthorityEvent.Refresh -> coroutineScope.launch {
                    phase = AccountAuthorityPhase.Loading
                    load()
                }
                AccountAuthorityEvent.StartAdoption -> coroutineScope.launch {
                    errorMessage = null
                    authorityManager.beginAdoption()
                        .onSuccess { offer ->
                            recoveryArtifact = offer.recoveryArtifact
                            artifactConfirmed = false
                            stepUp = AccountAuthorityStepUp.Adopt
                            phase = AccountAuthorityPhase.Artifact
                        }
                        .onFailure {
                            errorMessage = R.string.screen_account_authority_error_generic
                        }
                }
                is AccountAuthorityEvent.ConfirmArtifactStored -> {
                    artifactConfirmed = event.confirmed
                }
                AccountAuthorityEvent.ContinueFromArtifact -> {
                    if (artifactConfirmed && recoveryArtifact != null) {
                        askForStepUp(stepUp ?: AccountAuthorityStepUp.Adopt)
                    }
                }
                AccountAuthorityEvent.StartRecovery -> {
                    recoveryArtifactInput = ""
                    recoveryArtifactError = null
                    errorMessage = null
                    recoveryRoute = AccountAuthorityRecoveryRoute.RecoveryKey
                    phase = AccountAuthorityPhase.RecoveryEntry
                }
                AccountAuthorityEvent.StartAccountRecovery -> {
                    if (canRecoverThroughAccountRecovery(chain)) {
                        recoveryArtifactInput = ""
                        recoveryArtifactError = null
                        errorMessage = null
                        accountRecoveryAcknowledged = false
                        recoveryRoute = AccountAuthorityRecoveryRoute.AccountRecovery
                        phase = AccountAuthorityPhase.AccountRecoveryNotice
                    }
                }
                is AccountAuthorityEvent.AcknowledgeAccountRecovery -> {
                    accountRecoveryAcknowledged = event.acknowledged
                }
                AccountAuthorityEvent.ContinueFromAccountRecoveryNotice -> coroutineScope.launch {
                    if (!accountRecoveryAcknowledged) return@launch
                    phase = AccountAuthorityPhase.Submitting
                    authorityManager.beginAccountRecovery()
                        .onSuccess { offer ->
                            recoveryArtifact = offer.recoveryArtifact
                            artifactConfirmed = false
                            stepUp = AccountAuthorityStepUp.Recover
                            phase = AccountAuthorityPhase.Artifact
                        }
                        .onFailure {
                            errorMessage = R.string.screen_account_authority_error_generic
                            phase = AccountAuthorityPhase.AccountRecoveryNotice
                        }
                }
                is AccountAuthorityEvent.RecoveryArtifactChanged -> {
                    recoveryArtifactInput = event.artifact
                    recoveryArtifactError = null
                }
                AccountAuthorityEvent.ContinueFromRecoveryEntry -> coroutineScope.launch {
                    phase = AccountAuthorityPhase.Submitting
                    authorityManager.beginRecovery(recoveryArtifactInput)
                        .onSuccess { offer ->
                            recoveryArtifact = offer.recoveryArtifact
                            artifactConfirmed = false
                            stepUp = AccountAuthorityStepUp.Recover
                            phase = AccountAuthorityPhase.Artifact
                        }
                        .onFailure { error ->
                            recoveryArtifactError = error.toArtifactMessageRes()
                            phase = AccountAuthorityPhase.RecoveryEntry
                        }
                }
                AccountAuthorityEvent.OfferThisDevice -> coroutineScope.launch {
                    val accessToken = accessToken() ?: return@launch
                    errorMessage = null
                    authorityManager.offerThisDeviceForGrant(accessToken, AuthorityDeviceLabel.current())
                        .onSuccess { candidate ->
                            thisDeviceFingerprint = AuthorityFingerprint.grouped(candidate.fingerprint)
                        }
                        .onFailure { error -> errorMessage = error.toMessageRes() }
                }
                is AccountAuthorityEvent.SelectCandidate -> {
                    val candidate = candidates.firstOrNull { it.deviceKeyB64Url == event.deviceKeyB64Url }
                    if (candidate != null) {
                        selectedCandidate = candidate
                        fingerprintConfirmed = false
                        errorMessage = null
                        phase = AccountAuthorityPhase.Compare
                    }
                }
                is AccountAuthorityEvent.ConfirmFingerprint -> {
                    fingerprintConfirmed = event.confirmed
                }
                AccountAuthorityEvent.ContinueFromCompare -> {
                    if (fingerprintConfirmed && selectedCandidate != null) {
                        askForStepUp(AccountAuthorityStepUp.Grant)
                    }
                }
                is AccountAuthorityEvent.StartRevocation -> {
                    val device = chain?.devices?.firstOrNull { it.deviceKeyB64Url == event.deviceKeyB64Url }
                    if (device != null) {
                        val activeCount = chain?.devices.orEmpty().count { it.isActive }
                        revocationTarget = AuthorityRevocationTarget(
                            deviceKeyB64Url = device.deviceKeyB64Url,
                            label = device.label,
                            isThisDevice = device.deviceKeyB64Url == thisDeviceKey,
                            targetMayObject = activeCount == 2 && device.deviceKeyB64Url != thisDeviceKey,
                        )
                        askForStepUp(AccountAuthorityStepUp.Revoke)
                    }
                }
                is AccountAuthorityEvent.PinChanged -> {
                    pin = event.pin.filter { it.isDigit() }.take(AccountAuthorityState.PIN_LENGTH)
                    errorMessage = null
                }
                AccountAuthorityEvent.Submit -> coroutineScope.launch {
                    if (stepUpMethod == AccountAuthorityStepUpMethod.WebSheet) return@launch
                    submitStepUp(AuthorityStepUp.Pin(pin))
                }
                AccountAuthorityEvent.ConfirmInBrowser -> coroutineScope.launch {
                    if (stepUpMethod != AccountAuthorityStepUpMethod.WebSheet) return@launch
                    openWebStepUp()
                }
                AccountAuthorityEvent.ClearWebStepUpUrl -> {
                    webStepUpUrl = null
                }
                AccountAuthorityEvent.Oppose -> coroutineScope.launch {
                    val accessToken = accessToken() ?: return@launch
                    val currentChain = chain ?: return@launch
                    val pending = currentChain.pending ?: return@launch
                    phase = AccountAuthorityPhase.Submitting
                    // Routed by the pending record type, not by key possession: the adopting phone holds a key before the chain
                    // has any device.
                    val outcome = if (pending.needsADeviceToOppose) {
                        authorityManager.opposeWithRecord(accessToken, currentChain)
                    } else {
                        authorityManager.oppose(accessToken, pending.recordHash, pin = null)
                    }
                    outcome
                        .onSuccess {
                            successMessage = R.string.screen_account_authority_opposed
                            load()
                        }
                        .onFailure { error ->
                            if (error is AuthorityError.StepUpRequired) {
                                askForStepUp(AccountAuthorityStepUp.Oppose)
                            } else {
                                errorMessage = error.toMessageRes()
                                phase = AccountAuthorityPhase.Overview
                            }
                        }
                }
                is AccountAuthorityEvent.Approve -> coroutineScope.launch {
                    val accessToken = accessToken() ?: return@launch
                    val currentChain = chain ?: return@launch
                    val approval = approvals.firstOrNull { it.approvalId == event.approvalId } ?: return@launch
                    phase = AccountAuthorityPhase.Submitting
                    authorityManager.approve(accessToken, currentChain, approval)
                        .onSuccess {
                            successMessage = R.string.screen_account_authority_approved
                        }
                        .onFailure { error -> errorMessage = error.toMessageRes() }
                    load()
                }
                is AccountAuthorityEvent.RemoveSecurityNotification -> {
                    val row = securityNotifications.firstOrNull { it.installationId == event.installationId }
                    if (row != null) {
                        notificationRemovalTarget = row
                        askForStepUp(AccountAuthorityStepUp.RemoveNotification)
                    }
                }
                AccountAuthorityEvent.Cancel -> resetFlow()
                AccountAuthorityEvent.ClearSuccess -> {
                    successMessage = null
                }
            }
        }

        return AccountAuthorityState(
            featureEnabled = featureEnabled,
            phase = phase,
            chain = chain,
            unavailable = unavailable,
            deviceHoldsAuthority = deviceHoldsAuthority,
            thisDeviceKeyB64Url = thisDeviceKey,
            recoveryArtifact = recoveryArtifact,
            artifactConfirmed = artifactConfirmed,
            recoveryArtifactInput = recoveryArtifactInput,
            recoveryArtifactError = recoveryArtifactError,
            candidates = candidates,
            selectedCandidate = selectedCandidate,
            fingerprintConfirmed = fingerprintConfirmed,
            thisDeviceFingerprint = thisDeviceFingerprint,
            revocationTarget = revocationTarget,
            stepUp = stepUp,
            stepUpBlock = stepUpBlock,
            stepUpMethod = stepUpMethod,
            webStepUpUrl = webStepUpUrl,
            awaitingWebStepUp = awaitingWebStepUp,
            recoveryRoute = recoveryRoute,
            accountRecoveryAcknowledged = accountRecoveryAcknowledged,
            canRecoverThroughAccountRecovery = canRecoverThroughAccountRecovery(chain),
            securityNotifications = securityNotifications,
            thisInstallationId = thisInstallationId,
            notificationChannelAvailable = notificationChannelAvailable,
            notificationRemovalTarget = notificationRemovalTarget,
            pin = pin,
            approvals = approvals,
            errorMessage = errorMessage,
            successMessage = successMessage,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun accessToken(): String? =
        sessionStore.getSession(matrixClient.sessionId.value)?.accessToken
}

private val AccountFactorStatus.hasStrongFactor: Boolean get() = passkeyRegistered || hasPin

private fun AccountAuthorityStepUp.toPurpose(): AuthorityPurpose? = when (this) {
    AccountAuthorityStepUp.Adopt -> AuthorityPurpose.ADOPT
    AccountAuthorityStepUp.Grant -> AuthorityPurpose.GRANT
    AccountAuthorityStepUp.Revoke -> AuthorityPurpose.REVOKE
    AccountAuthorityStepUp.Recover -> AuthorityPurpose.RECOVER
    AccountAuthorityStepUp.Oppose -> null
    AccountAuthorityStepUp.RemoveNotification -> null
}

private fun canRecoverThroughAccountRecovery(chain: AuthorityChainState?): Boolean = chain != null &&
    chain.state != AuthorityChainState.STATE_BOOTSTRAP &&
    chain.accountClass != ACCOUNT_CLASS_GENESIS &&
    chain.pending == null

private const val ACCOUNT_CLASS_GENESIS = "GENESIS"

private fun Throwable.toMessageRes(): Int = when (this) {
    is AuthorityError.StepUpRequired -> R.string.screen_account_authority_error_step_up_required
    is AuthorityError.StepUpUnavailable -> R.string.screen_account_authority_step_up_none
    is AuthorityError.FactorTooFresh -> R.string.screen_account_authority_error_factor_too_fresh
    is AuthorityError.RecoveryTooRecent -> R.string.screen_account_authority_error_recovery_too_recent
    is AuthorityError.ArtifactUnconfirmed -> R.string.screen_account_authority_error_artifact_unconfirmed
    is AuthorityError.NativeSessionRequired -> R.string.screen_account_authority_error_native_session
    is AuthorityError.PositionRefused -> R.string.screen_account_authority_error_position_refused
    is AuthorityError.PendingConflict -> R.string.screen_account_authority_error_pending_conflict
    is AuthorityError.HeadConflict -> R.string.screen_account_authority_error_head_conflict
    is AuthorityError.LastDevice -> R.string.screen_account_authority_error_last_device
    is AuthorityError.SignerRefused -> R.string.screen_account_authority_error_signer_refused
    is AuthorityError.DeviceQuarantined -> R.string.screen_account_authority_error_quarantined
    is AuthorityError.UnknownCandidate -> R.string.screen_account_authority_error_unknown_candidate
    is AuthorityError.NoNotificationChannel -> R.string.screen_account_authority_error_no_channel
    is AuthorityError.OppositionStale -> R.string.screen_account_authority_error_head_conflict
    is AuthorityError.Backoff, is AuthorityError.Cooldown -> R.string.screen_account_authority_error_backoff
    is AuthorityError.ApprovalInvalid, is AuthorityError.ApprovalLimit ->
        R.string.screen_account_authority_error_approval_invalid
    is AuthorityError.OppositionDeviceRequired, is AuthorityError.OppositionRefused ->
        R.string.screen_account_authority_error_opposition_refused
    else -> R.string.screen_account_authority_error_generic
}

private fun Throwable.toArtifactMessageRes(): Int = when ((this as? InvalidAuthorityRecordException)?.reason) {
    "bad_artifact_prefix" -> R.string.screen_account_authority_recovery_artifact_wrong_kind
    "bad_artifact_length" -> R.string.screen_account_authority_recovery_artifact_incomplete
    "bad_artifact" -> R.string.screen_account_authority_recovery_artifact_invalid
    else -> R.string.screen_account_authority_error_generic
}
