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
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthoritySubmission

/**
 * GUA FORK: an [AccountAuthorityManager] for the grant offer's tests, recording what it was asked so a test
 * can assert that declining the offer sends nothing at all.
 */
class FakeAccountAuthorityManager(
    private val stateResult: () -> Result<AuthorityChainState> = { Result.success(aRootedChain()) },
    private val grantResult: () -> Result<AuthoritySubmission> = {
        Result.success(AuthoritySubmission(seq = 2, state = "ACTIVE", effectiveAtEpochSeconds = 0, recordHash = "h"))
    },
) : AccountAuthorityManager {
    data class GrantCall(val granteeDeviceKeyB64Url: String, val label: String, val pin: String?)

    val grantCalls: MutableList<GrantCall> = mutableListOf()
    val stateCalls: MutableList<String> = mutableListOf()

    override suspend fun state(accessToken: String): Result<AuthorityChainState> {
        stateCalls += accessToken
        return stateResult()
    }

    override suspend fun holdsAuthority(): Boolean = true

    override suspend fun beginAdoption(): Result<AdoptionOffer> =
        Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun adopt(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        pin: String?,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> = Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit> =
        Result.failure(UnsupportedOperationException("not part of this flow"))

    override suspend fun grantDevice(
        accessToken: String,
        chain: AuthorityChainState,
        granteeDeviceKeyB64Url: String,
        label: String,
        pin: String?,
    ): Result<AuthoritySubmission> {
        grantCalls += GrantCall(granteeDeviceKeyB64Url, label, pin)
        return grantResult()
    }

    override suspend fun approvals(accessToken: String): Result<List<AuthorityApproval>> =
        Result.success(emptyList())

    override suspend fun approve(
        accessToken: String,
        chain: AuthorityChainState,
        approval: AuthorityApproval,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this flow"))
}

fun aRootedChain(): AuthorityChainState = AuthorityChainState(
    accountId = "ga1zzzz",
    accountClass = "BOOTSTRAP",
    state = AuthorityChainState.STATE_ROOTED,
    headSeq = 1,
    headHash = "11".repeat(32),
    devices = emptyList(),
    pending = null,
)
