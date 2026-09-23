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
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.guaresolver.authority.AccountAuthorityManager
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.launch

/**
 * GUA FORK: presenter for the account authority screen (ADM-009).
 *
 * It does three things, and refuses to do a fourth. It shows what the chain says about this account, in the
 * chain's own terms: a quarantined device is drawn as quarantined and a pending transition is drawn with the
 * time it completes, because a screen that flattened those into "your devices" would be claiming authority
 * the account does not hold yet. It runs the adoption of decision 4, in the order that decision fixes: keys,
 * then the artifact, then the step-up, then the record. And it signs the browser approvals of decision 6.
 *
 * It refuses to reach any of that while [FeatureFlags.AccountAuthority] is off: with the flag off the state
 * is a single inert value, no session is read, and no request is made. The screen is not reachable either,
 * because the settings row that opens it is behind the same flag, so this is the second of two gates rather
 * than the only one.
 *
 * THE STEP-UP IS THE ACCOUNT PIN, AND THERE IS NO SMS. Decision 4 accepts a user-verifying passkey assertion
 * or the PIN, and this build has no WebAuthn ceremony to produce an assertion with, exactly as the
 * change-phone screen already states about itself. An account whose only factor is a passkey is therefore
 * told that its passkey cannot be used here, and is never offered a code to its phone: decision 9 forbids a
 * phone code in any accepted set for an authority operation, so "fall back to SMS" is not a branch that may
 * exist in this file.
 */
@Inject
class AccountAuthorityPresenter(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val featureFlagService: FeatureFlagService,
    private val authorityManager: AccountAuthorityManager,
) : Presenter<AccountAuthorityState> {
    @Composable
    override fun present(): AccountAuthorityState {
        val coroutineScope = rememberCoroutineScope()

        var featureEnabled by remember { mutableStateOf(false) }
        var phase by remember { mutableStateOf(AccountAuthorityPhase.Loading) }
        var chain by remember { mutableStateOf<AuthorityChainState?>(null) }
        var unavailable by remember { mutableStateOf(false) }
        var deviceHoldsAuthority by remember { mutableStateOf(false) }
        var recoveryArtifact by remember { mutableStateOf<String?>(null) }
        var artifactConfirmed by remember { mutableStateOf(false) }
        var stepUp by remember { mutableStateOf<AccountAuthorityStepUp?>(null) }
        var pin by remember { mutableStateOf("") }
        var approvals by remember { mutableStateOf<List<AuthorityApproval>>(emptyList()) }
        var errorMessage by remember { mutableStateOf<Int?>(null) }
        var successMessage by remember { mutableStateOf<Int?>(null) }

        suspend fun load() {
            // The flag is read first and nothing else happens while it is off. This is what the flags-off
            // test asserts: no session read, no request, no state beyond "off".
            val enabled = featureFlagService.isFeatureEnabled(FeatureFlags.AccountAuthority)
            featureEnabled = enabled
            if (!enabled) {
                phase = AccountAuthorityPhase.Overview
                return
            }
            deviceHoldsAuthority = authorityManager.holdsAuthority()
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
                    // Only an account whose chain recognises a key on this device can sign an approval, so
                    // only then is there anything to show. Asking for them otherwise would be a request
                    // whose answer this screen could do nothing with.
                    approvals = if (deviceHoldsAuthority) {
                        authorityManager.approvals(accessToken).getOrElse { emptyList() }
                    } else {
                        emptyList()
                    }
                }
                .onFailure { error ->
                    // "This deployment does not have the feature" is the normal case today and is not an
                    // error worth showing anyone, which is what the wire contract asks of a client.
                    unavailable = error is AuthorityError.Disabled
                    chain = null
                    errorMessage = if (unavailable) null else error.toMessageRes()
                }
            phase = AccountAuthorityPhase.Overview
        }

        LaunchedEffect(Unit) { load() }

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
                    // The gate, not a decoration on the button: an event that arrives without the
                    // confirmation leaves the user where they were.
                    if (artifactConfirmed && recoveryArtifact != null) {
                        stepUp = AccountAuthorityStepUp.Adopt
                        pin = ""
                        errorMessage = null
                        phase = AccountAuthorityPhase.StepUp
                    }
                }
                is AccountAuthorityEvent.PinChanged -> {
                    pin = event.pin.filter { it.isDigit() }.take(AccountAuthorityState.PIN_LENGTH)
                    errorMessage = null
                }
                AccountAuthorityEvent.Submit -> coroutineScope.launch {
                    val accessToken = accessToken() ?: return@launch
                    when (stepUp) {
                        AccountAuthorityStepUp.Adopt -> {
                            val currentChain = chain ?: return@launch
                            phase = AccountAuthorityPhase.Submitting
                            authorityManager.adopt(
                                accessToken = accessToken,
                                chain = currentChain,
                                deviceLabel = AuthorityDeviceLabel.current(),
                                stepUp = AuthorityStepUp.Pin(pin),
                                artifactConfirmed = artifactConfirmed,
                            )
                                .onSuccess {
                                    // The artifact is dropped the moment the record is in: it is a private
                                    // key, and the screen that showed it has done its job.
                                    recoveryArtifact = null
                                    artifactConfirmed = false
                                    pin = ""
                                    stepUp = null
                                    errorMessage = null
                                    successMessage = R.string.screen_account_authority_submitted
                                    load()
                                }
                                .onFailure { error ->
                                    errorMessage = error.toMessageRes()
                                    pin = ""
                                    phase = AccountAuthorityPhase.StepUp
                                }
                        }
                        AccountAuthorityStepUp.Oppose -> {
                            phase = AccountAuthorityPhase.Submitting
                            authorityManager.oppose(accessToken, recordHash = chain?.pending?.recordHash, pin = pin)
                                .onSuccess {
                                    pin = ""
                                    stepUp = null
                                    errorMessage = null
                                    successMessage = R.string.screen_account_authority_opposed
                                    load()
                                }
                                .onFailure { error ->
                                    errorMessage = error.toMessageRes()
                                    pin = ""
                                    phase = AccountAuthorityPhase.StepUp
                                }
                        }
                        null -> Unit
                    }
                }
                AccountAuthorityEvent.Oppose -> coroutineScope.launch {
                    val accessToken = accessToken() ?: return@launch
                    val recordHash = chain?.pending?.recordHash
                    phase = AccountAuthorityPhase.Submitting
                    authorityManager.oppose(accessToken, recordHash, pin = null)
                        .onSuccess {
                            successMessage = R.string.screen_account_authority_opposed
                            load()
                        }
                        .onFailure { error ->
                            if (error is AuthorityError.StepUpRequired) {
                                // The second and later opposition needs a factor, at any age. The hold never
                                // gates an objection, so an owner who has just changed their PIN to lock a
                                // thief out is not the one disarmed by it.
                                stepUp = AccountAuthorityStepUp.Oppose
                                pin = ""
                                errorMessage = null
                                phase = AccountAuthorityPhase.StepUp
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
                AccountAuthorityEvent.Cancel -> {
                    pin = ""
                    stepUp = null
                    errorMessage = null
                    // The artifact is not shown again on a cancel. The keys it belongs to are still in the
                    // adoption slot and a new attempt mints a new pair, so nothing is lost and no secret is
                    // held on a screen the user walked away from.
                    recoveryArtifact = null
                    artifactConfirmed = false
                    phase = AccountAuthorityPhase.Overview
                }
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
            recoveryArtifact = recoveryArtifact,
            artifactConfirmed = artifactConfirmed,
            stepUp = stepUp,
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

/**
 * Maps one refusal onto the copy that says what to do about it.
 *
 * Each of these is a different thing for the user, which is why they are not one "something went wrong": a
 * backoff is a wait, a quarantine is a different wait, a position refusal is never going to succeed, and a
 * fresh-factor hold is the account being protected from the very laundering path decision 8 describes.
 */
private fun Throwable.toMessageRes(): Int = when (this) {
    is AuthorityError.StepUpRequired -> R.string.screen_account_authority_error_step_up_required
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
    is AuthorityError.Backoff, is AuthorityError.Cooldown -> R.string.screen_account_authority_error_backoff
    is AuthorityError.ApprovalInvalid, is AuthorityError.ApprovalLimit ->
        R.string.screen_account_authority_error_approval_invalid
    is AuthorityError.OppositionDeviceRequired, is AuthorityError.OppositionRefused ->
        R.string.screen_account_authority_error_opposition_refused
    else -> R.string.screen_account_authority_error_generic
}
