/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import io.element.android.libraries.guaresolver.genesis.Base64Url

/**
 * GUA FORK: an [AccountAuthorityClient] that records what it was asked, so a test can assert both what was
 * sent and what was NOT: no challenge may be minted for an adoption whose recovery artifact was never
 * confirmed, no grant may be signed over a candidate nobody compared, and nothing in this feature may ever
 * carry a phone code.
 */
class FakeAccountAuthorityClient(
    private val challengeResult: (AuthorityPurpose) -> Result<AuthorityChallenge> = {
        Result.success(AuthorityChallenge(challengeB64Url = A_CHALLENGE_B64, expiresInSeconds = 900))
    },
    private val adoptResult: () -> Result<AuthoritySubmission> = { Result.success(aSubmission()) },
    private val grantResult: () -> Result<AuthoritySubmission> = {
        Result.success(aSubmission(state = "ACTIVE", seq = 2))
    },
    private val revokeResult: () -> Result<AuthoritySubmission> = { Result.success(aSubmission(seq = 3)) },
    private val recoverResult: () -> Result<AuthoritySubmission> = { Result.success(aSubmission(seq = 2)) },
    private val opposeResult: () -> Result<Unit> = { Result.success(Unit) },
    private val opposeRecordResult: () -> Result<Unit> = { Result.success(Unit) },
    private val candidateResult: (String) -> Result<AuthorityCandidate> = { key ->
        Result.success(
            AuthorityCandidate(
                deviceKeyB64Url = key,
                fingerprint = AuthorityFingerprint.of(Base64Url.decode(key)),
                label = "a new phone",
                expiresAtEpochSeconds = 0,
            )
        )
    },
    private val candidatesResult: () -> Result<List<AuthorityCandidate>> = { Result.success(emptyList()) },
    private val stateResult: () -> Result<AuthorityChainState> = { Result.success(aBootstrapChain()) },
    private val approvalsResult: () -> Result<List<AuthorityApproval>> = { Result.success(emptyList()) },
    private val signApprovalResult: () -> Result<Unit> = { Result.success(Unit) },
    private val registerNotificationResult: () -> Result<Unit> = { Result.success(Unit) },
    private val notificationsResult: () -> Result<List<SecurityNotificationView>> = {
        Result.success(emptyList())
    },
    private val removeNotificationResult: () -> Result<Unit> = { Result.success(Unit) },
    private val webStepUpResult: (AuthorityPurpose) -> Result<String> = {
        Result.success(A_STEP_UP_URL)
    },
) : AccountAuthorityClient {
    data class ChallengeCall(val purpose: AuthorityPurpose, val stepUp: AuthorityStepUp)

    val challengeCalls: MutableList<ChallengeCall> = mutableListOf()
    val adoptCalls: MutableList<AuthorityRecordSubmission> = mutableListOf()
    val grantCalls: MutableList<AuthorityRecordSubmission> = mutableListOf()
    val revokeCalls: MutableList<AuthorityRecordSubmission> = mutableListOf()
    val recoverCalls: MutableList<AuthorityRecordSubmission> = mutableListOf()
    val opposeCalls: MutableList<Pair<String?, String?>> = mutableListOf()
    val opposeRecordCalls: MutableList<AuthorityRecordSubmission> = mutableListOf()
    val candidateCalls: MutableList<Pair<String, String?>> = mutableListOf()
    val signApprovalCalls: MutableList<Pair<String, String>> = mutableListOf()
    val registerNotificationCalls: MutableList<SecurityNotificationRegistration> = mutableListOf()
    val removeNotificationCalls: MutableList<SecurityNotificationRemoval> = mutableListOf()
    val webStepUpCalls: MutableList<AuthorityPurpose> = mutableListOf()

    override suspend fun challenge(
        accessToken: String,
        purpose: AuthorityPurpose,
        stepUp: AuthorityStepUp,
    ): Result<AuthorityChallenge> {
        challengeCalls += ChallengeCall(purpose, stepUp)
        return challengeResult(purpose)
    }

    override suspend fun startWebStepUp(accessToken: String, purpose: AuthorityPurpose): Result<String> {
        webStepUpCalls += purpose
        return webStepUpResult(purpose)
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

    override suspend fun revokeDevice(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission> {
        revokeCalls += submission
        return revokeResult()
    }

    override suspend fun recoverAuthority(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission> {
        recoverCalls += submission
        return recoverResult()
    }

    override suspend fun offerCandidate(
        accessToken: String,
        deviceKeyB64Url: String,
        label: String?,
    ): Result<AuthorityCandidate> {
        candidateCalls += deviceKeyB64Url to label
        return candidateResult(deviceKeyB64Url)
    }

    override suspend fun candidates(accessToken: String): Result<List<AuthorityCandidate>> = candidatesResult()

    override suspend fun registerSecurityNotification(
        accessToken: String,
        registration: SecurityNotificationRegistration,
    ): Result<Unit> {
        registerNotificationCalls += registration
        return registerNotificationResult()
    }

    override suspend fun securityNotifications(
        accessToken: String,
    ): Result<List<SecurityNotificationView>> = notificationsResult()

    override suspend fun removeSecurityNotification(
        accessToken: String,
        removal: SecurityNotificationRemoval,
    ): Result<Unit> {
        removeNotificationCalls += removal
        return removeNotificationResult()
    }

    override suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit> {
        opposeCalls += recordHash to pin
        return opposeResult()
    }

    override suspend fun opposeWithRecord(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<Unit> {
        opposeRecordCalls += submission
        return opposeRecordResult()
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

        /** The one-time URL a web step-up runs at, on the sign-in web origin. */
        const val A_STEP_UP_URL = "https://auth.example.org/login/enroll/AbCdEf"
    }
}
