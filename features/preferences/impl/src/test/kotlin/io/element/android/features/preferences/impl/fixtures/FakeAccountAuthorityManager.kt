/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.fixtures

import io.element.android.libraries.guaresolver.authority.AccountAuthorityManager
import io.element.android.libraries.guaresolver.authority.AdoptionOffer
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityDevice
import io.element.android.libraries.guaresolver.authority.AuthorityPendingTransition
import io.element.android.libraries.guaresolver.authority.AuthorityPurpose
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.guaresolver.authority.AuthoritySubmission
import io.element.android.libraries.guaresolver.authority.SecurityNotificationView

/**
 * GUA FORK: an [AccountAuthorityManager] that records what it was asked, so a test can assert what was sent
 * and, just as importantly, what was not: with the feature flag off nothing here may be called at all.
 */
class FakeAccountAuthorityManager(
    private val stateResult: () -> Result<AuthorityChainState> = { Result.success(aBootstrapChain()) },
    private val holdsAuthorityResult: () -> Boolean = { false },
    private val beginAdoptionResult: () -> Result<AdoptionOffer> = {
        Result.success(AdoptionOffer(recoveryArtifact = A_RECOVERY_ARTIFACT))
    },
    private val adoptResult: () -> Result<AuthoritySubmission> = { Result.success(aSubmission()) },
    private val opposeResult: () -> Result<Unit> = { Result.success(Unit) },
    private val grantResult: () -> Result<AuthoritySubmission> = { Result.success(aSubmission()) },
    private val opposeRecordResult: () -> Result<Unit> = { Result.success(Unit) },
    private val revokeResult: () -> Result<AuthoritySubmission> = { Result.success(aSubmission()) },
    private val beginRecoveryResult: (String) -> Result<AdoptionOffer> = {
        Result.success(AdoptionOffer(recoveryArtifact = A_RECOVERY_ARTIFACT))
    },
    private val recoverResult: () -> Result<AuthoritySubmission> = { Result.success(aSubmission()) },
    private val candidatesResult: () -> Result<List<AuthorityCandidate>> = { Result.success(emptyList()) },
    private val offerCandidateResult: () -> Result<AuthorityCandidate> = { Result.success(aCandidate()) },
    private val approvalsResult: () -> Result<List<AuthorityApproval>> = { Result.success(emptyList()) },
    private val approveResult: () -> Result<Unit> = { Result.success(Unit) },
    private val notificationsResult: () -> Result<List<SecurityNotificationView>> = {
        Result.success(emptyList())
    },
    private val registerNotificationsResult: () -> Result<Unit> = { Result.success(Unit) },
    private val removeNotificationResult: () -> Result<Unit> = { Result.success(Unit) },
    private val webStepUpResult: (AuthorityPurpose) -> Result<String> = { Result.success(A_STEP_UP_URL) },
    private val beginAccountRecoveryResult: () -> Result<AdoptionOffer> = {
        Result.success(AdoptionOffer(recoveryArtifact = A_RECOVERY_ARTIFACT))
    },
    private val recoverThroughAccountRecoveryResult: () -> Result<AuthoritySubmission> = {
        Result.success(aSubmission())
    },
) : AccountAuthorityManager {
    data class AdoptCall(val deviceLabel: String, val stepUp: AuthorityStepUp, val artifactConfirmed: Boolean)

    data class GrantCall(
        val candidate: AuthorityCandidate,
        val stepUp: AuthorityStepUp,
        val fingerprintConfirmed: Boolean,
    )

    data class RevokeCall(val deviceKeyB64Url: String, val reason: Int, val stepUp: AuthorityStepUp)

    data class RecoverCall(
        val artifact: String,
        val deviceLabel: String,
        val stepUp: AuthorityStepUp,
        val artifactConfirmed: Boolean,
    )

    val stateCalls: MutableList<String> = mutableListOf()
    val beginAdoptionCalls: MutableList<Unit> = mutableListOf()
    val adoptCalls: MutableList<AdoptCall> = mutableListOf()
    val opposeCalls: MutableList<Pair<String?, String?>> = mutableListOf()
    val opposeRecordCalls: MutableList<AuthorityChainState> = mutableListOf()
    val grantCalls: MutableList<GrantCall> = mutableListOf()
    val revokeCalls: MutableList<RevokeCall> = mutableListOf()
    val beginRecoveryCalls: MutableList<String> = mutableListOf()
    val recoverCalls: MutableList<RecoverCall> = mutableListOf()
    val approveCalls: MutableList<String> = mutableListOf()
    val registerNotificationCalls: MutableList<String> = mutableListOf()
    val removeNotificationCalls: MutableList<Pair<String, String?>> = mutableListOf()
    val webStepUpCalls: MutableList<AuthorityPurpose> = mutableListOf()
    val beginAccountRecoveryCalls: MutableList<Unit> = mutableListOf()
    val accountRecoveryCalls: MutableList<RecoverCall> = mutableListOf()

    override suspend fun state(accessToken: String): Result<AuthorityChainState> {
        stateCalls += accessToken
        return stateResult()
    }

    override suspend fun holdsAuthority(): Boolean = holdsAuthorityResult()

    override suspend fun authorityDeviceKeyB64Url(): String? =
        if (holdsAuthorityResult()) A_DEVICE_KEY else null

    override suspend fun beginAdoption(): Result<AdoptionOffer> {
        beginAdoptionCalls += Unit
        return beginAdoptionResult()
    }

    override suspend fun adopt(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> {
        adoptCalls += AdoptCall(deviceLabel, stepUp, artifactConfirmed)
        return adoptResult()
    }

    override suspend fun startWebStepUp(accessToken: String, purpose: AuthorityPurpose): Result<String> {
        webStepUpCalls += purpose
        return webStepUpResult(purpose)
    }

    override suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit> {
        opposeCalls += recordHash to pin
        return opposeResult()
    }

    override suspend fun opposeWithRecord(
        accessToken: String,
        chain: AuthorityChainState,
    ): Result<Unit> {
        opposeRecordCalls += chain
        return opposeRecordResult()
    }

    override suspend fun offerThisDeviceForGrant(
        accessToken: String,
        label: String,
    ): Result<AuthorityCandidate> = offerCandidateResult()

    override suspend fun candidates(accessToken: String): Result<List<AuthorityCandidate>> = candidatesResult()

    override suspend fun grantDevice(
        accessToken: String,
        chain: AuthorityChainState,
        candidate: AuthorityCandidate,
        stepUp: AuthorityStepUp,
        fingerprintConfirmed: Boolean,
    ): Result<AuthoritySubmission> {
        grantCalls += GrantCall(candidate, stepUp, fingerprintConfirmed)
        return grantResult()
    }

    override suspend fun revokeDevice(
        accessToken: String,
        chain: AuthorityChainState,
        deviceKeyB64Url: String,
        reason: Int,
        stepUp: AuthorityStepUp,
    ): Result<AuthoritySubmission> {
        revokeCalls += RevokeCall(deviceKeyB64Url, reason, stepUp)
        return revokeResult()
    }

    override suspend fun beginRecovery(recoveryArtifact: String): Result<AdoptionOffer> {
        beginRecoveryCalls += recoveryArtifact
        return beginRecoveryResult(recoveryArtifact)
    }

    override suspend fun recoverAuthority(
        accessToken: String,
        chain: AuthorityChainState,
        recoveryArtifact: String,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> {
        recoverCalls += RecoverCall(recoveryArtifact, deviceLabel, stepUp, artifactConfirmed)
        return recoverResult()
    }

    override suspend fun beginAccountRecovery(): Result<AdoptionOffer> {
        beginAccountRecoveryCalls += Unit
        return beginAccountRecoveryResult()
    }

    override suspend fun recoverThroughAccountRecovery(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> {
        // The artifact a 0x02 record commits is the NEW one, so there is no old artifact in this call. The
        // field is empty rather than absent so the two routes can be compared in one assertion.
        accountRecoveryCalls += RecoverCall("", deviceLabel, stepUp, artifactConfirmed)
        return recoverThroughAccountRecoveryResult()
    }

    override suspend fun approvals(accessToken: String): Result<List<AuthorityApproval>> = approvalsResult()

    override suspend fun approve(
        accessToken: String,
        chain: AuthorityChainState,
        approval: AuthorityApproval,
    ): Result<Unit> {
        approveCalls += approval.approvalId
        return approveResult()
    }

    override suspend fun registerSecurityNotifications(
        accessToken: String,
        pushToken: String,
        platform: String,
        appId: String,
        deviceLabel: String,
    ): Result<Unit> {
        registerNotificationCalls += pushToken
        return registerNotificationsResult()
    }

    override suspend fun securityNotifications(
        accessToken: String,
    ): Result<List<SecurityNotificationView>> = notificationsResult()

    override suspend fun removeSecurityNotification(
        accessToken: String,
        installationId: String,
        pin: String?,
    ): Result<Unit> {
        removeNotificationCalls += installationId to pin
        return removeNotificationResult()
    }

    override suspend fun installationId(): String = AN_INSTALLATION_ID
}

const val A_RECOVERY_ARTIFACT: String = "gua-recovery-1 abcd efgh ijkl mnop"

const val AN_ACCOUNT_ID: String = "ga1zzzz"

const val A_DEVICE_KEY: String = "a-device-key"

const val AN_INSTALLATION_ID: String = "an-installation"

/** The one-time URL a web step-up runs at, on the sign-in web origin. */
const val A_STEP_UP_URL: String = "https://auth.example.org/login/enroll/AbCdEf"

fun aBootstrapChain(pending: AuthorityPendingTransition? = null): AuthorityChainState = AuthorityChainState(
    accountId = AN_ACCOUNT_ID,
    accountClass = "BOOTSTRAP",
    state = if (pending == null) {
        AuthorityChainState.STATE_BOOTSTRAP
    } else {
        AuthorityChainState.STATE_ADOPTION_PENDING
    },
    headSeq = 0,
    headHash = "0".repeat(64),
    devices = emptyList(),
    pending = pending,
)

fun aRootedChain(
    devices: List<AuthorityDevice>,
    pending: AuthorityPendingTransition? = null,
): AuthorityChainState = AuthorityChainState(
    accountId = AN_ACCOUNT_ID,
    accountClass = "BOOTSTRAP",
    state = AuthorityChainState.STATE_ROOTED,
    headSeq = 2,
    headHash = "11".repeat(32),
    devices = devices,
    pending = pending,
)

/** A chain whose account lost every device and its recovery key: terminal, by ADM-009 decision 7. */
fun anAuthorityLostChain(): AuthorityChainState = AuthorityChainState(
    accountId = AN_ACCOUNT_ID,
    accountClass = "BOOTSTRAP",
    state = AuthorityChainState.STATE_AUTHORITY_LOST,
    headSeq = 3,
    headHash = "22".repeat(32),
    devices = emptyList(),
    pending = null,
)

/**
 * A chain whose accountId commits its authority (class 0x01), where the account-recovery route is refused.
 *
 * The server refuses a 0x02 record on such an account outright: a genesis-committed authority is replaced only
 * by the key the genesis committed for that purpose. The screen has to withhold the offer rather than send one.
 */
fun aGenesisRootedChain(
    devices: List<AuthorityDevice> = emptyList(),
): AuthorityChainState = AuthorityChainState(
    accountId = AN_ACCOUNT_ID,
    accountClass = "GENESIS",
    state = AuthorityChainState.STATE_ROOTED,
    headSeq = 2,
    headHash = "33".repeat(32),
    devices = devices,
    pending = null,
)

fun aCandidate(
    deviceKeyB64Url: String = "a-candidate-key",
    fingerprint: String = "9KDCZT8A",
    label: String = "Pixel Tablet",
): AuthorityCandidate = AuthorityCandidate(
    deviceKeyB64Url = deviceKeyB64Url,
    fingerprint = fingerprint,
    label = label,
    expiresAtEpochSeconds = 1_800_000_600,
)

fun anAuthorityDevice(
    label: String = "Pixel 9",
    state: String = "ACTIVE",
    quarantineUntilEpochSeconds: Long? = null,
    deviceKeyB64Url: String = A_DEVICE_KEY,
): AuthorityDevice = AuthorityDevice(
    deviceKeyB64Url = deviceKeyB64Url,
    label = label,
    state = state,
    quarantineUntilEpochSeconds = quarantineUntilEpochSeconds,
    grantedSeq = 1,
)

fun aPendingAdoption(
    effectiveAtEpochSeconds: Long = 1_800_000_000,
    recordHash: String = "a-record-hash",
    type: String = "ADOPT_ROOT",
    seq: Long = 1,
): AuthorityPendingTransition = AuthorityPendingTransition(
    type = type,
    seq = seq,
    effectiveAtEpochSeconds = effectiveAtEpochSeconds,
    recordHash = recordHash,
)

fun aSubmission(): AuthoritySubmission = AuthoritySubmission(
    seq = 1,
    state = "PENDING",
    effectiveAtEpochSeconds = 1_800_000_000,
    recordHash = "a-record-hash",
)

fun anApproval(
    approvalId: String = "an-approval",
    code: String = "AB7K",
): AuthorityApproval = AuthorityApproval(
    approvalId = approvalId,
    code = code,
    action = "add-recovery-contact",
    actionDigestB64Url = "a-digest",
    challengeB64Url = "a-challenge",
    expiresAtEpochSeconds = 1_800_000_600,
)
