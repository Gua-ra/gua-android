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

    /**
     * What the account-recovery route costs, before anything is minted (authorization 0x02).
     *
     * A screen of its own for the same reason the artifact has one: this route is the weaker of the two and
     * every device the account still has can stop it, so the owner has to read that and say so before a record
     * exists, not while a spinner runs.
     */
    AccountRecoveryNotice,

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
 * Why this account cannot produce the step-up this step asks for.
 *
 * A passkey-only account is NOT here for any of the four transitions: its assertion runs in the web sheet,
 * which is the whole point of [AccountAuthorityStepUpMethod.WebSheet]. It is here for an objection, which is
 * the one step-up that has no sheet, and even then the copy offers the other way out rather than a weaker
 * factor to add.
 */
enum class AccountAuthorityStepUpBlock {
    /** The account holds no factor at all, so no transition here can be authorized. */
    NoFactorRegistered,

    /**
     * A second or later objection on an account that holds a passkey and no PIN.
     *
     * An objection takes a factor at any age and has no web sheet, because the sheet exists for the purposes
     * that ask for one and an objection is not a transition: the server's own policy answers "no factor
     * required" for it, so there is nothing to record a proof against. An assertion for it would have to run
     * natively, which this platform cannot do. The honest out is the other objection, the signed one an active
     * device makes for free, so that is what the copy says. It still does not say to add a PIN.
     */
    PasskeyNotUsableForObjection,
}

/**
 * Where the step-up ADM-009 decision 4 asks for is produced.
 *
 * The branch is over what the server says the account HOLDS, and never over what this build finds convenient.
 * An account with a passkey confirms in the web sheet, because that is where a user-verifying assertion can
 * run at all on this platform; an account with only a PIN is asked for it here, because a PIN needs no browser.
 * A factor status that could not be read takes the sheet as well: the page offers whichever factor the account
 * actually holds, so it is the answer that cannot be wrong, and guessing the PIN is how a passkey holder ends
 * up staring at a field they have nothing to type into.
 */
enum class AccountAuthorityStepUpMethod {
    /**
     * The one-time page identity-service serves, opened in a Custom Tab exactly as first-PIN enrollment is.
     *
     * The proof it leaves behind is a row the server wrote, bound to this account, this access token and this
     * one purpose, so nothing comes back through the app but the fact that the sheet closed.
     */
    WebSheet,

    /** The account PIN, typed here. The fallback factor, and never a code sent to a number. */
    Pin,
}

/** Which of the two `AuthorityRecovery` routes is on screen. They are not the same transition. */
enum class AccountAuthorityRecoveryRoute {
    /**
     * Authorization 0x01: signed by the recovery authority key the account committed. Rank 2, which no pending
     * record can block and no intruder holding every device can cancel.
     */
    RecoveryKey,

    /**
     * Authorization 0x02: authorized by the account's own credentials after an account recovery, and signed by
     * the device key the record installs because it names no authorizing key at all.
     *
     * Rank 0. Any device that still holds this account's authority can veto it outright while it waits, a
     * rank-1 or rank-2 record cancels it, and the server refuses it on an account whose id commits its
     * authority. That weakness is what makes it safe to offer to someone who has lost the artifact, and it is
     * what the screen has to say before it is started.
     */
    AccountRecovery,
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
    /** Where this step-up is produced: the web sheet where the account holds a passkey, the PIN otherwise. */
    val stepUpMethod: AccountAuthorityStepUpMethod?,
    /**
     * The one-time web step-up URL to open in a Custom Tab, held only until the view has opened it.
     *
     * Cleared as soon as it has been handed over, so returning to this screen does not reopen a sheet whose
     * proof was already spent.
     */
    val webStepUpUrl: String?,
    /** True from the moment a sheet was opened until the app is back and the transition has been attempted. */
    val awaitingWebStepUp: Boolean,
    /** Which `AuthorityRecovery` route the flow on screen is, or null when no recovery is in flight. */
    val recoveryRoute: AccountAuthorityRecoveryRoute?,
    /** That the owner read what the account-recovery route costs and said so. */
    val accountRecoveryAcknowledged: Boolean,
    /**
     * True when the account-recovery route can be offered at all: the chain holds a committed authority, its id
     * does NOT commit that authority (a class 0x01 account is refused this route outright), and nothing else is
     * pending. Read from the chain rather than from a local flag, so the offer follows what the server accepts.
     */
    val canRecoverThroughAccountRecovery: Boolean,
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

    val canContinueFromAccountRecoveryNotice: Boolean = accountRecoveryAcknowledged && !isWorking

    /**
     * Everything a step-up needs except the factor itself: the transition is known, its own acknowledgement was
     * given, and nothing this screen can produce is missing.
     *
     * Kept apart from [canSubmit] because the two factors are asked for in different places. What must NOT
     * differ between them is this list, so a gate added here holds for the sheet and the PIN alike.
     */
    val stepUpGatesPassed: Boolean = when {
        isWorking || stepUpBlock != null -> false
        stepUp == null -> false
        stepUp == AccountAuthorityStepUp.Adopt && !artifactConfirmed -> false
        stepUp == AccountAuthorityStepUp.Recover && !artifactConfirmed -> false
        stepUp == AccountAuthorityStepUp.Grant && !fingerprintConfirmed -> false
        else -> true
    }

    /** A step-up that cannot be produced is not offered: the block is shown instead of a PIN field. */
    val canSubmit: Boolean = stepUpGatesPassed &&
        stepUpMethod != AccountAuthorityStepUpMethod.WebSheet &&
        pin.length == PIN_LENGTH

    /**
     * Whether the web sheet may be opened. No PIN in this condition: the page asks for the factor, and this
     * screen's job is to have the transition and its acknowledgement settled before the browser opens.
     *
     * Still true while a sheet is outstanding, deliberately. A tab that never opened, on a phone with no
     * browser or one that refused the intent, would otherwise leave a person looking at a button they can no
     * longer press. Asking again is safe because the server burns any earlier unspent proof of the same
     * account, session and purpose, so one transition still ends up with one proof.
     */
    val canConfirmInBrowser: Boolean = stepUpGatesPassed &&
        stepUpMethod == AccountAuthorityStepUpMethod.WebSheet

    companion object {
        const val PIN_LENGTH = 6
    }
}
