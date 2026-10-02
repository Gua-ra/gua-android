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
import io.element.android.libraries.guaresolver.authority.SecurityNotificationView

enum class AccountAuthorityPhase {
    Loading,
    Overview,

    Artifact,

    RecoveryEntry,

    AccountRecoveryNotice,

    Compare,
    StepUp,
    Submitting,
}

enum class AccountAuthorityStepUp {
    Adopt,

    Oppose,

    Grant,

    Revoke,

    Recover,

    RemoveNotification,
}

enum class AccountAuthorityStepUpBlock {
    NoFactorRegistered,

    PasskeyNotUsableForObjection,

    PasskeyNotUsableForNotificationRemoval,
}

enum class AccountAuthorityStepUpMethod {
    WebSheet,

    Pin,
}

enum class AccountAuthorityRecoveryRoute {
    RecoveryKey,

    AccountRecovery,
}

data class AuthorityRevocationTarget(
    val deviceKeyB64Url: String,
    val label: String,
    val isThisDevice: Boolean,
    val targetMayObject: Boolean,
)

data class AccountAuthorityState(
    val featureEnabled: Boolean,
    val phase: AccountAuthorityPhase,
    val chain: AuthorityChainState?,
    val unavailable: Boolean,
    val deviceHoldsAuthority: Boolean,
    val thisDeviceKeyB64Url: String?,
    val recoveryArtifact: String?,
    val artifactConfirmed: Boolean,
    val recoveryArtifactInput: String,
    @StringRes val recoveryArtifactError: Int?,
    val candidates: List<AuthorityCandidate>,
    val selectedCandidate: AuthorityCandidate?,
    val fingerprintConfirmed: Boolean,
    val thisDeviceFingerprint: String?,
    val revocationTarget: AuthorityRevocationTarget?,
    val stepUp: AccountAuthorityStepUp?,
    val stepUpBlock: AccountAuthorityStepUpBlock?,
    val stepUpMethod: AccountAuthorityStepUpMethod?,
    val webStepUpUrl: String?,
    val awaitingWebStepUp: Boolean,
    val recoveryRoute: AccountAuthorityRecoveryRoute?,
    val accountRecoveryAcknowledged: Boolean,
    val canRecoverThroughAccountRecovery: Boolean,
    val securityNotifications: List<SecurityNotificationView>,
    val thisInstallationId: String?,
    val notificationChannelAvailable: Boolean,
    val notificationRemovalTarget: SecurityNotificationView?,
    val pin: String,
    val approvals: List<AuthorityApproval>,
    @StringRes val errorMessage: Int?,
    @StringRes val successMessage: Int?,
    val eventSink: (AccountAuthorityEvent) -> Unit,
) {
    val isWorking: Boolean = phase == AccountAuthorityPhase.Submitting

    val canAdopt: Boolean = chain?.canAdopt == true

    val authorityLost: Boolean = chain?.state == AuthorityChainState.STATE_AUTHORITY_LOST

    val recoveryPending: Boolean = chain?.state == AuthorityChainState.STATE_RECOVERY_PENDING

    val pendingTransition = chain?.pending

    val devices: List<AuthorityDevice> = chain?.devices.orEmpty()

    val activeDevices: List<AuthorityDevice> = devices.filter { it.isActive }

    val canRecover: Boolean = chain != null &&
        !canAdopt &&
        chain.state != AuthorityChainState.STATE_BOOTSTRAP &&
        chain.pending == null

    val showsSecurityNotifications: Boolean = notificationChannelAvailable && !unavailable

    fun isThisInstall(registration: SecurityNotificationView): Boolean =
        thisInstallationId != null && registration.installationId == thisInstallationId

    val canOfferThisDevice: Boolean = chain != null &&
        !deviceHoldsAuthority &&
        chain.state == AuthorityChainState.STATE_ROOTED

    val canContinueFromArtifact: Boolean = artifactConfirmed && recoveryArtifact != null

    val canContinueFromRecoveryEntry: Boolean = recoveryArtifactInput.isNotBlank() && !isWorking

    val canContinueFromCompare: Boolean = fingerprintConfirmed && selectedCandidate != null

    val canContinueFromAccountRecoveryNotice: Boolean = accountRecoveryAcknowledged && !isWorking

    val stepUpGatesPassed: Boolean = when {
        isWorking || stepUpBlock != null -> false
        stepUp == null -> false
        stepUp == AccountAuthorityStepUp.Adopt && !artifactConfirmed -> false
        stepUp == AccountAuthorityStepUp.Recover && !artifactConfirmed -> false
        stepUp == AccountAuthorityStepUp.Grant && !fingerprintConfirmed -> false
        else -> true
    }

    val canSubmit: Boolean = stepUpGatesPassed &&
        stepUpMethod != AccountAuthorityStepUpMethod.WebSheet &&
        pin.length == PIN_LENGTH

    /** Stays true while a sheet is outstanding, so a tab that never opened can be retried; the server keeps one proof. */
    val canConfirmInBrowser: Boolean = stepUpGatesPassed &&
        stepUpMethod == AccountAuthorityStepUpMethod.WebSheet

    companion object {
        const val PIN_LENGTH = 6
    }
}
