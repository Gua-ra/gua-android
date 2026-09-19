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
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityDevice
import io.element.android.libraries.guaresolver.authority.AuthorityPendingTransition
import io.element.android.libraries.guaresolver.authority.AuthoritySubmission

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
    private val approvalsResult: () -> Result<List<AuthorityApproval>> = { Result.success(emptyList()) },
    private val approveResult: () -> Result<Unit> = { Result.success(Unit) },
) : AccountAuthorityManager {
    data class AdoptCall(val deviceLabel: String, val pin: String?, val artifactConfirmed: Boolean)

    val stateCalls: MutableList<String> = mutableListOf()
    val beginAdoptionCalls: MutableList<Unit> = mutableListOf()
    val adoptCalls: MutableList<AdoptCall> = mutableListOf()
    val opposeCalls: MutableList<Pair<String?, String?>> = mutableListOf()
    val grantCalls: MutableList<Pair<String, String?>> = mutableListOf()
    val approveCalls: MutableList<String> = mutableListOf()

    override suspend fun state(accessToken: String): Result<AuthorityChainState> {
        stateCalls += accessToken
        return stateResult()
    }

    override suspend fun holdsAuthority(): Boolean = holdsAuthorityResult()

    override suspend fun beginAdoption(): Result<AdoptionOffer> {
        beginAdoptionCalls += Unit
        return beginAdoptionResult()
    }

    override suspend fun adopt(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        pin: String?,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> {
        adoptCalls += AdoptCall(deviceLabel, pin, artifactConfirmed)
        return adoptResult()
    }

    override suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit> {
        opposeCalls += recordHash to pin
        return opposeResult()
    }

    override suspend fun grantDevice(
        accessToken: String,
        chain: AuthorityChainState,
        granteeDeviceKeyB64Url: String,
        label: String,
        pin: String?,
    ): Result<AuthoritySubmission> {
        grantCalls += granteeDeviceKeyB64Url to pin
        return grantResult()
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
}

const val A_RECOVERY_ARTIFACT: String = "gua-recovery-1 abcd efgh ijkl mnop"

const val AN_ACCOUNT_ID: String = "ga1zzzz"

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

fun aRootedChain(devices: List<AuthorityDevice>): AuthorityChainState = AuthorityChainState(
    accountId = AN_ACCOUNT_ID,
    accountClass = "BOOTSTRAP",
    state = AuthorityChainState.STATE_ROOTED,
    headSeq = 2,
    headHash = "11".repeat(32),
    devices = devices,
    pending = null,
)

fun anAuthorityDevice(
    label: String = "Pixel 9",
    state: String = "ACTIVE",
    quarantineUntilEpochSeconds: Long? = null,
): AuthorityDevice = AuthorityDevice(
    deviceKeyB64Url = "a-device-key",
    label = label,
    state = state,
    quarantineUntilEpochSeconds = quarantineUntilEpochSeconds,
    grantedSeq = 1,
)

fun aPendingAdoption(
    effectiveAtEpochSeconds: Long = 1_800_000_000,
    recordHash: String = "a-record-hash",
): AuthorityPendingTransition = AuthorityPendingTransition(
    type = "ADOPT_ROOT",
    seq = 1,
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
