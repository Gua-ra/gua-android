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

/**
 * GUA FORK: sample states of the account authority screen (ADM-009), for previews and screenshot tests.
 *
 * It exists because the view was the one layer of this screen nothing rendered. The presenter has tests and
 * the codec has golden vectors, and the screen itself was read off the composition by eye: the quarantine row,
 * the two-device carve-out footer, the security-alerts rows and the step-up blocks all came from state the
 * view builds itself, and none of it was ever drawn in a test.
 */
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

/**
 * The overview of a rooted account, with everything the screen can draw on it at once.
 *
 * Defaults deliberately land on the state a reader is most likely to be in, so a preview or a test names only
 * what it is about. Nothing here mints a key or reads a keystore: it is data the presenter would have produced.
 */
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

/** A device inside its own grant window, which may sign nothing and counts toward no device requirement. */
fun aQuarantinedDevice() = anAuthorityDevice(
    deviceKeyB64Url = "another-device-key",
    label = "iPhone 16",
    state = "QUARANTINED",
    quarantineUntilEpochSeconds = A_FIXED_INSTANT + 259_200,
    grantedSeq = 2,
)

/**
 * One install the security-notification channel would warn.
 *
 * The token is not here because the server never returns one: a row is named by the fingerprint it keeps and
 * by when it was last seen, which is what lets an owner recognise a phone without a destination on screen.
 */
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

/** A transition inside its window, which already holds its seq. */
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

/**
 * One live browser approval waiting for a signature.
 *
 * The action id is the opaque one the page chose. The screen describes it in its own words and never renders
 * this string, which is the property a rendered test is worth having for: a malicious page can start an
 * approval, and its text must not reach the screen that is supposed to be the independent one.
 */
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

/** 2026-01-02 03:04:05 UTC, fixed so a screenshot of a date is a screenshot of the same date tomorrow. */
const val A_FIXED_INSTANT: Long = 1_767_322_845
