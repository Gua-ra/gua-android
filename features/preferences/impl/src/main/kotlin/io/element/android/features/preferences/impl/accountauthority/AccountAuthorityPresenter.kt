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
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.authority.AccountAuthorityManager
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityDeviceLabel
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityFingerprint
import io.element.android.libraries.guaresolver.authority.AuthorityRecord
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.guaresolver.authority.InvalidAuthorityRecordException
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.launch

/**
 * GUA FORK: presenter for the account authority screen (ADM-009).
 *
 * It shows what the chain says about this account, in the chain's own terms: a quarantined device is drawn as
 * quarantined, a pending transition is drawn with the time it completes, and an account that has lost its
 * authority is told so rather than offered a second adoption. And it runs the whole device lifecycle, each
 * transition in the order its decision fixes: adoption, a grant over a candidate whose fingerprint was
 * compared, a revocation of another device or of this one, the signed objection an active device makes, and a
 * recovery authorized by the artifact the owner kept.
 *
 * It refuses to reach any of that while [FeatureFlags.AccountAuthority] is off: with the flag off the state
 * is a single inert value, no session is read, and no request is made. The screen is not reachable either,
 * because the settings row that opens it is behind the same flag, so this is the second of two gates rather
 * than the only one.
 *
 * THE STEP-UP, AND WHY A PASSKEY-ONLY ACCOUNT IS NOT SENT TO SET A PIN. Decision 4 accepts a user-verifying
 * passkey assertion or the PIN, and never a phone code. The branch here is over what the server says the
 * account HOLDS, read before anything is offered, and never over `hasPin` alone. An account with a passkey and
 * no PIN is told that its passkey cannot be asserted on this build, which is this app's gap: the identity
 * service exposes WebAuthn request options for a native ceremony this client does not have, and the web
 * ceremony it does have registers a passkey rather than asserting one. It is never told to add a PIN, because
 * it already has two-step verification and the PIN would be a weaker factor added for our convenience.
 */
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
                    // Only an account whose chain recognises a key on this device can sign an approval or a
                    // grant, so only then is there anything to ask for. Asking otherwise would be a request
                    // whose answer this screen could do nothing with.
                    approvals = if (deviceHoldsAuthority) {
                        authorityManager.approvals(accessToken).getOrElse { emptyList() }
                    } else {
                        emptyList()
                    }
                    candidates = if (deviceHoldsAuthority) {
                        authorityManager.candidates(accessToken).getOrElse { emptyList() }
                            // A candidate whose fingerprint this client could not recompute is not shown:
                            // eight characters nobody derived from those 32 bytes bind nothing.
                            .filter { it.fingerprint.isNotEmpty() }
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

        /**
         * Reads the account's factors before offering a step-up, and answers with the block when this build
         * cannot produce one. A read that fails leaves the block null: the factors are UNKNOWN, not absent,
         * and guessing "no factor" is how a passkey holder gets told to create a PIN.
         */
        suspend fun blockingStepUp(): AccountAuthorityStepUpBlock? {
            val accessToken = accessToken() ?: return null
            val status = identityServiceClient.accountFactorStatus(
                accessToken = accessToken,
                userId = matrixClient.sessionId.value,
            ).getOrNull() ?: return null
            return when {
                !status.hasStrongFactor -> AccountAuthorityStepUpBlock.NoFactorRegistered
                !status.hasPin -> AccountAuthorityStepUpBlock.PasskeyNotUsableHere
                else -> null
            }
        }

        /** Enters the step-up for [kind], or the block that says why this account cannot produce one. */
        fun askForStepUp(kind: AccountAuthorityStepUp) {
            coroutineScope.launch {
                pin = ""
                errorMessage = null
                stepUp = kind
                stepUpBlock = blockingStepUp()
                phase = AccountAuthorityPhase.StepUp
            }
        }

        fun resetFlow() {
            pin = ""
            stepUp = null
            stepUpBlock = null
            errorMessage = null
            // The artifact is not shown again on a cancel. The keys it belongs to are still in the adoption
            // slot and a new attempt mints a new pair, so nothing is lost and no secret is held on a screen
            // the user walked away from.
            recoveryArtifact = null
            artifactConfirmed = false
            recoveryArtifactInput = ""
            recoveryArtifactError = null
            selectedCandidate = null
            fingerprintConfirmed = false
            revocationTarget = null
            phase = AccountAuthorityPhase.Overview
        }

        suspend fun submitStepUp() {
            val accessToken = accessToken() ?: return
            val currentChain = chain ?: return
            // Everything the submission needs is resolved before the screen says it is working, so a step
            // whose subject went missing leaves the user on the step-up rather than on a spinner.
            val candidate = selectedCandidate
            val target = revocationTarget
            if (stepUp == AccountAuthorityStepUp.Grant && candidate == null) return
            if (stepUp == AccountAuthorityStepUp.Revoke && target == null) return
            val factor = AuthorityStepUp.Pin(pin)
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
                        // The reason is a fixed byte, so nothing the owner typed can end up in the
                        // notification the other devices see.
                        reason = if (target?.isThisDevice == true) {
                            AuthorityRecord.REASON_REPLACED
                        } else {
                            AuthorityRecord.REASON_UNSPECIFIED
                        },
                        stepUp = factor,
                    ).map { }
                }
                AccountAuthorityStepUp.Recover -> authorityManager.recoverAuthority(
                    accessToken = accessToken,
                    chain = currentChain,
                    recoveryArtifact = recoveryArtifactInput,
                    deviceLabel = AuthorityDeviceLabel.current(),
                    stepUp = factor,
                    artifactConfirmed = artifactConfirmed,
                ).map { }
                null -> return
            }
            outcome
                .onSuccess {
                    val message = when (stepUp) {
                        AccountAuthorityStepUp.Oppose -> R.string.screen_account_authority_opposed
                        AccountAuthorityStepUp.Revoke -> R.string.screen_account_authority_revoked
                        else -> R.string.screen_account_authority_submitted
                    }
                    resetFlow()
                    successMessage = message
                    load()
                }
                .onFailure { error ->
                    errorMessage = error.toMessageRes()
                    pin = ""
                    phase = AccountAuthorityPhase.StepUp
                }
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
                    // The gate, not a decoration on the button: an event that arrives without the
                    // confirmation leaves the user where they were.
                    if (artifactConfirmed && recoveryArtifact != null) {
                        askForStepUp(stepUp ?: AccountAuthorityStepUp.Adopt)
                    }
                }
                AccountAuthorityEvent.StartRecovery -> {
                    recoveryArtifactInput = ""
                    recoveryArtifactError = null
                    errorMessage = null
                    phase = AccountAuthorityPhase.RecoveryEntry
                }
                is AccountAuthorityEvent.RecoveryArtifactChanged -> {
                    recoveryArtifactInput = event.artifact
                    recoveryArtifactError = null
                }
                AccountAuthorityEvent.ContinueFromRecoveryEntry -> coroutineScope.launch {
                    phase = AccountAuthorityPhase.Submitting
                    authorityManager.beginRecovery(recoveryArtifactInput)
                        .onSuccess { offer ->
                            // The NEW artifact this recovery commits, shown once like the first one: the
                            // record replaces the recovery key as well as the device set.
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
                            // Decision 5's carve-out: on an account with two active devices, removing one
                            // would leave the signer alone, so the named device may object to its own removal.
                            targetMayObject = activeCount == 2 && device.deviceKeyB64Url != thisDeviceKey,
                        )
                        askForStepUp(AccountAuthorityStepUp.Revoke)
                    }
                }
                is AccountAuthorityEvent.PinChanged -> {
                    pin = event.pin.filter { it.isDigit() }.take(AccountAuthorityState.PIN_LENGTH)
                    errorMessage = null
                }
                AccountAuthorityEvent.Submit -> coroutineScope.launch { submitStepUp() }
                AccountAuthorityEvent.Oppose -> coroutineScope.launch {
                    val accessToken = accessToken() ?: return@launch
                    val currentChain = chain ?: return@launch
                    phase = AccountAuthorityPhase.Submitting
                    // A device that holds authority objects with a signed record, which is the only objection
                    // the server accepts against a grant, a revocation or a recovery. A session may object to
                    // an adoption and nothing else, which is the one case where no device can exist yet.
                    val outcome = if (deviceHoldsAuthority) {
                        authorityManager.opposeWithRecord(accessToken, currentChain)
                    } else {
                        authorityManager.oppose(accessToken, currentChain.pending?.recordHash, pin = null)
                    }
                    outcome
                        .onSuccess {
                            successMessage = R.string.screen_account_authority_opposed
                            load()
                        }
                        .onFailure { error ->
                            if (error is AuthorityError.StepUpRequired) {
                                // The second and later session opposition needs a factor, at any age. The
                                // hold never gates an objection, so an owner who has just changed their PIN
                                // to lock a thief out is not the one disarmed by it.
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

/** The account holds a strong factor when it holds either, which is what decides whether a step-up is possible. */
private val AccountFactorStatus.hasStrongFactor: Boolean get() = passkeyRegistered || hasPin

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
    is AuthorityError.UnknownCandidate -> R.string.screen_account_authority_error_unknown_candidate
    // A legitimate owner can reach this one, so it says what to do rather than reading as a fault.
    is AuthorityError.NoNotificationChannel -> R.string.screen_account_authority_error_no_channel
    is AuthorityError.OppositionStale -> R.string.screen_account_authority_error_head_conflict
    is AuthorityError.Backoff, is AuthorityError.Cooldown -> R.string.screen_account_authority_error_backoff
    is AuthorityError.ApprovalInvalid, is AuthorityError.ApprovalLimit ->
        R.string.screen_account_authority_error_approval_invalid
    is AuthorityError.OppositionDeviceRequired, is AuthorityError.OppositionRefused ->
        R.string.screen_account_authority_error_opposition_refused
    else -> R.string.screen_account_authority_error_generic
}

/**
 * What is wrong with the artifact someone typed back, in their terms.
 *
 * The refusal reasons are the decoder's own, and each one is a different thing the person can do about it,
 * which is the whole reason this is checked here rather than letting the server answer "signature refused".
 */
private fun Throwable.toArtifactMessageRes(): Int = when ((this as? InvalidAuthorityRecordException)?.reason) {
    "bad_artifact_prefix" -> R.string.screen_account_authority_recovery_artifact_wrong_kind
    "bad_artifact_length" -> R.string.screen_account_authority_recovery_artifact_incomplete
    "bad_artifact" -> R.string.screen_account_authority_recovery_artifact_invalid
    else -> R.string.screen_account_authority_error_generic
}
