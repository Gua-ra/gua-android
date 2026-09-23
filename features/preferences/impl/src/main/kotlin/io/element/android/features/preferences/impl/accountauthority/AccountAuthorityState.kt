/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

import androidx.annotation.StringRes
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityDevice

/**
 * GUA FORK: where the account authority screen is (ADM-009).
 *
 * [Artifact] and [StepUp] are two screens rather than one because they ask for two different things and only
 * one of them can be skipped: the artifact must be read and acknowledged, and the step-up must be produced.
 * Collapsing them would let a user tick a box and type a PIN in the same breath, which is the acknowledgement
 * decision 7 wants being reduced to a formality. [Compare] is separate for the same kind of reason: it is the
 * only thing binding a key to the person holding the other phone.
 */
enum class AccountAuthorityPhase {
    Loading,
    Overview,

    /** The recovery artifact, shown once, whether it came from an adoption or from a recovery. */
    Artifact,

    /** Typing back an artifact the owner kept, for a recovery. */
    RecoveryEntry,

    /** Comparing a candidate's fingerprint against what the other phone shows. */
    Compare,
    StepUp,
    Submitting,
}

/** What the step-up on screen is for. Every transition asks for one, and they are not interchangeable. */
enum class AccountAuthorityStepUp {
    /** The scoped step-up ADM-009 decision 4 step 2 requires before an adoption. */
    Adopt,

    /** The second and later opposition, which takes any factor at any age (decision 4). */
    Oppose,

    /** Giving another device authority over this account. */
    Grant,

    /** Removing a device, whether another one or this one. */
    Revoke,

    /** Replacing the device set and the recovery key in one record. */
    Recover,
}

/**
 * Why this account cannot produce a step-up on this build.
 *
 * Neither case tells the owner to add a PIN. An account with a passkey already holds a strong factor, and
 * ADM-009 decision 4 accepts a passkey assertion; what is missing is a ceremony on this platform to produce
 * one, which is this app's gap and not the account's.
 */
enum class AccountAuthorityStepUpBlock {
    /** The account holds no factor at all, so no transition here can be authorized. */
    NoFactorRegistered,

    /**
     * The account holds a passkey and no PIN, and this build has no way to assert a passkey.
     *
     * The copy says that plainly and offers no PIN. There is nothing else honest to offer: the identity
     * service exposes WebAuthn request options for a native ceremony this app does not have, and the web
     * ceremony it does have registers a passkey rather than asserting one.
     */
    PasskeyNotUsableHere,
}

/** Which device a revocation on screen is about, because the two are not the same transition. */
data class AuthorityRevocationTarget(
    val deviceKeyB64Url: String,
    val label: String,
    /** This device's own key, which takes effect at once rather than after a window. */
    val isThisDevice: Boolean,
    /**
     * True when the account has exactly two active devices, so the device being removed may object to its own
     * removal (decision 5's carve-out). The copy says so, because an eviction the other side can veto is a
     * standoff and the owner should know before starting one.
     */
    val targetMayObject: Boolean,
)

data class AccountAuthorityState(
    /** The account-authority feature flag. While it is false this screen is not reachable at all. */
    val featureEnabled: Boolean,
    val phase: AccountAuthorityPhase,
    /**
     * The chain as the server reports it, or null when it could not be read. Null is UNKNOWN and never
     * "this account holds nothing": a failed read that rendered as an empty device set would invite the
     * one action this screen must never make easy.
     */
    val chain: AuthorityChainState?,
    /** True once the deployment has answered that it does not have this feature on. */
    val unavailable: Boolean,
    /** True while this device holds an authority key the chain recognises. */
    val deviceHoldsAuthority: Boolean,
    /** This device's own authority key, so its row can be drawn as this phone rather than as a stranger. */
    val thisDeviceKeyB64Url: String?,
    /**
     * The recovery artifact, held only while the artifact screen is on. It is the private recovery key, so
     * nothing keeps it after the record is submitted and nothing writes it anywhere.
     */
    val recoveryArtifact: String?,
    val artifactConfirmed: Boolean,
    /** What the owner typed back, for a recovery. Never logged and never sent: only a signature by it is. */
    val recoveryArtifactInput: String,
    @StringRes val recoveryArtifactError: Int?,
    /** Keys other devices have offered, each with a fingerprint this client recomputed from the key. */
    val candidates: List<AuthorityCandidate>,
    /** The candidate being compared, and whether the person said the eight characters match. */
    val selectedCandidate: AuthorityCandidate?,
    val fingerprintConfirmed: Boolean,
    /** This device's own fingerprint, once it has offered itself for a grant by another device. */
    val thisDeviceFingerprint: String?,
    val revocationTarget: AuthorityRevocationTarget?,
    val stepUp: AccountAuthorityStepUp?,
    val stepUpBlock: AccountAuthorityStepUpBlock?,
    val pin: String,
    val approvals: List<AuthorityApproval>,
    @StringRes val errorMessage: Int?,
    @StringRes val successMessage: Int?,
    val eventSink: (AccountAuthorityEvent) -> Unit,
) {
    val isWorking: Boolean = phase == AccountAuthorityPhase.Submitting

    /** Only a bootstrap account with an empty chain and nothing pending can adopt (decision 3 rule 3). */
    val canAdopt: Boolean = chain?.canAdopt == true

    /**
     * Terminal by decision 7: the account lost every device and its recovery key, keeps its id, its login and
     * its data, and never regains authority. A second adoption authorized by login factors alone is exactly
     * the seizure O9 rejected, so this screen offers none.
     */
    val authorityLost: Boolean = chain?.state == AuthorityChainState.STATE_AUTHORITY_LOST

    val recoveryPending: Boolean = chain?.state == AuthorityChainState.STATE_RECOVERY_PENDING

    val pendingTransition = chain?.pending

    val devices: List<AuthorityDevice> = chain?.devices.orEmpty()

    /** The device set the chain actually counts: a quarantined device counts for nothing. */
    val activeDevices: List<AuthorityDevice> = devices.filter { it.isActive }

    /** A recovery is offered while the account is rooted, because that is what the artifact is for. */
    val canRecover: Boolean = chain != null &&
        !canAdopt &&
        chain.state != AuthorityChainState.STATE_BOOTSTRAP &&
        chain.pending == null

    /** A device with no authority can ask the account's other devices to add it. */
    val canOfferThisDevice: Boolean = chain != null &&
        !deviceHoldsAuthority &&
        chain.state == AuthorityChainState.STATE_ROOTED

    /**
     * The artifact screen's only way forward. Adoption is unreachable without the confirmation: the button
     * is inert, the presenter refuses the event, and the manager refuses the submission.
     */
    val canContinueFromArtifact: Boolean = artifactConfirmed && recoveryArtifact != null

    val canContinueFromRecoveryEntry: Boolean = recoveryArtifactInput.isNotBlank() && !isWorking

    val canContinueFromCompare: Boolean = fingerprintConfirmed && selectedCandidate != null

    /** A step-up that cannot be produced is not offered: the block is shown instead of a PIN field. */
    val canSubmit: Boolean = when {
        isWorking || stepUpBlock != null -> false
        stepUp == null -> false
        stepUp == AccountAuthorityStepUp.Adopt && !artifactConfirmed -> false
        stepUp == AccountAuthorityStepUp.Recover && !artifactConfirmed -> false
        stepUp == AccountAuthorityStepUp.Grant && !fingerprintConfirmed -> false
        else -> pin.length == PIN_LENGTH
    }

    companion object {
        const val PIN_LENGTH = 6
    }
}
