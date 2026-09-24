/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.grantauthority

import io.element.android.libraries.guaresolver.authority.AccountAuthorityManager
import io.element.android.libraries.guaresolver.authority.AdoptionOffer
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityPurpose
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.guaresolver.authority.AuthoritySubmission
import io.element.android.libraries.guaresolver.authority.SecurityNotificationView

/**
 * GUA FORK: an [AccountAuthorityManager] for the grant offer's tests, recording what it was asked so a test
 * can assert that declining the offer sends nothing at all.
 */
class FakeAccountAuthorityManager(
    private val stateResult: () -> Result<AuthorityChainState> = { Result.success(aRootedChain()) },
    private val grantResult: () -> Result<AuthoritySubmission> = {
        Result.success(AuthoritySubmission(seq = 2, state = "ACTIVE", effectiveAtEpochSeconds = 0, recordHash = "h"))
    },
    private val holdsAuthorityResult: () -> Boolean = { true },
    private val candidatesResult: () -> Result<List<AuthorityCandidate>> = { Result.success(emptyList()) },
) : AccountAuthorityManager {
    data class GrantCall(
        val candidate: AuthorityCandidate,
        val stepUp: AuthorityStepUp,
        val fingerprintConfirmed: Boolean,
    )

    val grantCalls: MutableList<GrantCall> = mutableListOf()
    val stateCalls: MutableList<String> = mutableListOf()
    val candidatesCalls: MutableList<String> = mutableListOf()

    override suspend fun state(accessToken: String): Result<AuthorityChainState> {
        stateCalls += accessToken
        return stateResult()
    }

    override suspend fun holdsAuthority(): Boolean = holdsAuthorityResult()

    override suspend fun authorityDeviceKeyB64Url(): String? =
        "a-device-key".takeIf { holdsAuthorityResult() }

    override suspend fun beginAdoption(): Result<AdoptionOffer> =
        Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun startWebStepUp(accessToken: String, purpose: AuthorityPurpose): Result<String> =
        Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun beginAccountRecovery(): Result<AdoptionOffer> =
        Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun recoverThroughAccountRecovery(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> = Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun adopt(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> = Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit> =
        Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun opposeWithRecord(accessToken: String, chain: AuthorityChainState): Result<Unit> =
        Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun offerThisDeviceForGrant(
        accessToken: String,
        label: String,
    ): Result<AuthorityCandidate> = Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun candidates(accessToken: String): Result<List<AuthorityCandidate>> {
        candidatesCalls += accessToken
        return candidatesResult()
    }

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
    ): Result<AuthoritySubmission> = Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun beginRecovery(recoveryArtifact: String): Result<AdoptionOffer> =
        Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun recoverAuthority(
        accessToken: String,
        chain: AuthorityChainState,
        recoveryArtifact: String,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> = Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun approvals(accessToken: String): Result<List<AuthorityApproval>> =
        Result.success(emptyList())

    override suspend fun approve(
        accessToken: String,
        chain: AuthorityChainState,
        approval: AuthorityApproval,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun registerSecurityNotifications(
        accessToken: String,
        pushToken: String,
        platform: String,
        appId: String,
        deviceLabel: String,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun securityNotifications(
        accessToken: String,
    ): Result<List<SecurityNotificationView>> = Result.success(emptyList())

    override suspend fun removeSecurityNotification(
        accessToken: String,
        installationId: String,
        pin: String?,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun installationId(): String = "an-installation"
}

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

fun aRootedChain(): AuthorityChainState = AuthorityChainState(
    accountId = "ga1zzzz",
    accountClass = "BOOTSTRAP",
    state = AuthorityChainState.STATE_ROOTED,
    headSeq = 1,
    headHash = "11".repeat(32),
    devices = emptyList(),
    pending = null,
)
