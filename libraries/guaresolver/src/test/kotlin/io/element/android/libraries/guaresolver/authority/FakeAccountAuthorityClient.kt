/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

/**
 * GUA FORK: an [AccountAuthorityClient] that records what it was asked, so a test can assert both what was
 * sent and what was NOT: no challenge may be minted for an adoption whose recovery artifact was never
 * confirmed, and nothing in this feature may ever carry a phone code.
 */
class FakeAccountAuthorityClient(
    private val challengeResult: (AuthorityPurpose) -> Result<AuthorityChallenge> = {
        Result.success(AuthorityChallenge(challengeB64Url = A_CHALLENGE_B64, expiresInSeconds = 900))
    },
    private val adoptResult: () -> Result<AuthoritySubmission> = { Result.success(aSubmission()) },
    private val grantResult: () -> Result<AuthoritySubmission> = {
        Result.success(aSubmission(state = "ACTIVE", seq = 2))
    },
    private val opposeResult: () -> Result<Unit> = { Result.success(Unit) },
    private val stateResult: () -> Result<AuthorityChainState> = { Result.success(aBootstrapChain()) },
    private val approvalsResult: () -> Result<List<AuthorityApproval>> = { Result.success(emptyList()) },
    private val signApprovalResult: () -> Result<Unit> = { Result.success(Unit) },
) : AccountAuthorityClient {
    data class ChallengeCall(val purpose: AuthorityPurpose, val pin: String?)

    val challengeCalls: MutableList<ChallengeCall> = mutableListOf()
    val adoptCalls: MutableList<AuthorityRecordSubmission> = mutableListOf()
    val grantCalls: MutableList<AuthorityRecordSubmission> = mutableListOf()
    val opposeCalls: MutableList<Pair<String?, String?>> = mutableListOf()
    val signApprovalCalls: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun challenge(
        accessToken: String,
        purpose: AuthorityPurpose,
        pin: String?,
    ): Result<AuthorityChallenge> {
        challengeCalls += ChallengeCall(purpose, pin)
        return challengeResult(purpose)
    }

    override suspend fun adopt(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission> {
        adoptCalls += submission
        return adoptResult()
    }

    override suspend fun grantDevice(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission> {
        grantCalls += submission
        return grantResult()
    }

    override suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit> {
        opposeCalls += recordHash to pin
        return opposeResult()
    }

    override suspend fun state(accessToken: String): Result<AuthorityChainState> = stateResult()

    override suspend fun liveApprovals(accessToken: String): Result<List<AuthorityApproval>> = approvalsResult()

    override suspend fun signApproval(
        accessToken: String,
        approvalId: String,
        signatureB64Url: String,
    ): Result<Unit> {
        signApprovalCalls += approvalId to signatureB64Url
        return signApprovalResult()
    }

    companion object {
        /** 32 bytes, base64url without padding, as the server mints them. */
        const val A_CHALLENGE_B64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
    }
}
