/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

import androidx.annotation.StringRes
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityDevice

/**
 * GUA FORK: where the account authority screen is (ADM-009).
 *
 * [Artifact] and [StepUp] are two screens rather than one because they ask for two different things and only
 * one of them can be skipped: the artifact must be read and acknowledged, and the step-up must be produced.
 * Collapsing them would let a user tick a box and type a PIN in the same breath, which is the acknowledgement
 * decision 7 wants being reduced to a formality.
 */
enum class AccountAuthorityPhase {
    Loading,
    Overview,
    Artifact,
    StepUp,
    Submitting,
}

/** What the step-up on screen is for, since both transitions ask for the same factor. */
enum class AccountAuthorityStepUp {
    /** The scoped step-up ADM-009 decision 4 step 2 requires before an adoption. */
    Adopt,

    /** The second and later opposition, which takes any factor at any age (decision 4). */
    Oppose,
}

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
    /**
     * The recovery artifact, held only while the artifact screen is on. It is the private recovery key, so
     * nothing keeps it after the adoption is submitted and nothing writes it anywhere.
     */
    val recoveryArtifact: String?,
    val artifactConfirmed: Boolean,
    val stepUp: AccountAuthorityStepUp?,
    val pin: String,
    val approvals: List<AuthorityApproval>,
    @StringRes val errorMessage: Int?,
    @StringRes val successMessage: Int?,
    val eventSink: (AccountAuthorityEvent) -> Unit,
) {
    val isWorking: Boolean = phase == AccountAuthorityPhase.Submitting

    /** Only a bootstrap account with an empty chain and nothing pending can adopt (decision 3 rule 3). */
    val canAdopt: Boolean = chain?.canAdopt == true

    val pendingTransition = chain?.pending

    val devices: List<AuthorityDevice> = chain?.devices.orEmpty()

    /**
     * The artifact screen's only way forward. Adoption is unreachable without the confirmation: the button
     * is inert, the presenter refuses the event, and the manager refuses the submission.
     */
    val canContinueFromArtifact: Boolean = artifactConfirmed && recoveryArtifact != null

    val canSubmit: Boolean = when (stepUp) {
        AccountAuthorityStepUp.Adopt -> artifactConfirmed && pin.length == PIN_LENGTH && !isWorking
        AccountAuthorityStepUp.Oppose -> pin.length == PIN_LENGTH && !isWorking
        null -> false
    }

    companion object {
        const val PIN_LENGTH = 6
    }
}
