/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityDevice
import io.element.android.libraries.guaresolver.authority.AuthorityPendingTransition
import io.element.android.libraries.guaresolver.authority.SecurityNotificationView

open class AccountAuthorityStateProvider : PreviewParameterProvider<AccountAuthorityState> {
    override val values: Sequence<AccountAuthorityState>
        get() = sequenceOf(
            anAccountAuthorityState(),
            anAccountAuthorityState(
                chain = aRootedChain(devices = listOf(anAuthorityDevice(), aQuarantinedDevice())),
                deviceHoldsAuthority = true,
                thisDeviceKeyB64Url = A_DEVICE_KEY,
            ),
        )
}

@Suppress("LongParameterList")
fun anAccountAuthorityState(
    featureEnabled: Boolean = true,
    phase: AccountAuthorityPhase = AccountAuthorityPhase.Overview,
    chain: AuthorityChainState? = aRootedChain(),
    unavailable: Boolean = false,
    deviceHoldsAuthority: Boolean = true,
    thisDeviceKeyB64Url: String? = A_DEVICE_KEY,
    recoveryArtifact: String? = null,
    artifactConfirmed: Boolean = false,
    recoveryArtifactInput: String = "",
    recoveryArtifactError: Int? = null,
    candidates: List<AuthorityCandidate> = emptyList(),
    selectedCandidate: AuthorityCandidate? = null,
    fingerprintConfirmed: Boolean = false,
    thisDeviceFingerprint: String? = null,
    revocationTarget: AuthorityRevocationTarget? = null,
    stepUp: AccountAuthorityStepUp? = null,
    stepUpBlock: AccountAuthorityStepUpBlock? = null,
    stepUpMethod: AccountAuthorityStepUpMethod? = null,
    webStepUpUrl: String? = null,
    awaitingWebStepUp: Boolean = false,
    recoveryRoute: AccountAuthorityRecoveryRoute? = null,
    accountRecoveryAcknowledged: Boolean = false,
    canRecoverThroughAccountRecovery: Boolean = false,
    securityNotifications: List<SecurityNotificationView> = emptyList(),
    thisInstallationId: String? = AN_INSTALL_ID,
    notificationChannelAvailable: Boolean = false,
    notificationRemovalTarget: SecurityNotificationView? = null,
    pin: String = "",
    approvals: List<AuthorityApproval> = emptyList(),
    errorMessage: Int? = null,
    successMessage: Int? = null,
    eventSink: (AccountAuthorityEvent) -> Unit = {},
) = AccountAuthorityState(
    featureEnabled = featureEnabled,
    phase = phase,
    chain = chain,
    unavailable = unavailable,
    deviceHoldsAuthority = deviceHoldsAuthority,
    thisDeviceKeyB64Url = thisDeviceKeyB64Url,
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
    canRecoverThroughAccountRecovery = canRecoverThroughAccountRecovery,
    securityNotifications = securityNotifications,
    thisInstallationId = thisInstallationId,
    notificationChannelAvailable = notificationChannelAvailable,
    notificationRemovalTarget = notificationRemovalTarget,
    pin = pin,
    approvals = approvals,
    errorMessage = errorMessage,
    successMessage = successMessage,
    eventSink = eventSink,
)

fun aRootedChain(
    devices: List<AuthorityDevice> = listOf(anAuthorityDevice()),
    pending: AuthorityPendingTransition? = null,
    accountClass: String = "BOOTSTRAP",
    state: String = AuthorityChainState.STATE_ROOTED,
) = AuthorityChainState(
    accountId = AN_ACCOUNT,
    accountClass = accountClass,
    state = state,
    headSeq = 2,
    headHash = "11".repeat(32),
    devices = devices,
    pending = pending,
)

fun anAuthorityDevice(
    deviceKeyB64Url: String = A_DEVICE_KEY,
    label: String = "Pixel 9",
    state: String = "ACTIVE",
    quarantineUntilEpochSeconds: Long? = null,
    grantedSeq: Long = 1,
) = AuthorityDevice(
    deviceKeyB64Url = deviceKeyB64Url,
    label = label,
    state = state,
    quarantineUntilEpochSeconds = quarantineUntilEpochSeconds,
    grantedSeq = grantedSeq,
)

fun aQuarantinedDevice() = anAuthorityDevice(
    deviceKeyB64Url = "another-device-key",
    label = "iPhone 16",
    state = "QUARANTINED",
    quarantineUntilEpochSeconds = A_FIXED_INSTANT + 259_200,
    grantedSeq = 2,
)

fun aSecurityNotificationView(
    installationId: String = AN_INSTALL_ID,
    platform: String = "FCM",
    deviceLabel: String? = "Pixel 9",
    tokenFingerprint: String = "9f2a",
    boundToAnAuthorityDevice: Boolean = true,
    lastSeenAtEpochSeconds: Long = A_FIXED_INSTANT,
) = SecurityNotificationView(
    installationId = installationId,
    platform = platform,
    deviceLabel = deviceLabel,
    tokenFingerprint = tokenFingerprint,
    boundToAnAuthorityDevice = boundToAnAuthorityDevice,
    lastSeenAtEpochSeconds = lastSeenAtEpochSeconds,
)

fun aPendingTransition(
    type: String = "DEVICE_GRANT",
    seq: Long = 3,
    effectiveAtEpochSeconds: Long = A_FIXED_INSTANT + 259_200,
    recordHash: String = "22".repeat(32),
) = AuthorityPendingTransition(
    type = type,
    seq = seq,
    effectiveAtEpochSeconds = effectiveAtEpochSeconds,
    recordHash = recordHash,
)

fun anAuthorityApproval(
    approvalId: String = "an-approval",
    code: String = "AB7K",
    action: String? = "link-device",
) = AuthorityApproval(
    approvalId = approvalId,
    code = code,
    action = action,
    actionDigestB64Url = "a-digest",
    challengeB64Url = "a-challenge",
    expiresAtEpochSeconds = A_FIXED_INSTANT + 600,
)

const val A_DEVICE_KEY: String = "a-device-key"

const val AN_ACCOUNT: String = "ga1zzzz"

const val AN_INSTALL_ID: String = "an-installation"

const val A_FIXED_INSTANT: Long = 1_767_322_845
