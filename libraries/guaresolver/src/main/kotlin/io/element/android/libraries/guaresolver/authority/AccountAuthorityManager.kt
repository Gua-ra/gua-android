/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

interface AccountAuthorityManager {
    suspend fun state(accessToken: String): Result<AuthorityChainState>

    suspend fun holdsAuthority(): Boolean

    suspend fun authorityDeviceKeyB64Url(): String?

    suspend fun beginAdoption(): Result<AdoptionOffer>

    suspend fun adopt(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission>

    suspend fun startWebStepUp(accessToken: String, purpose: AuthorityPurpose): Result<String>

    suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit>

    suspend fun opposeWithRecord(accessToken: String, chain: AuthorityChainState): Result<Unit>

    suspend fun offerThisDeviceForGrant(accessToken: String, label: String): Result<AuthorityCandidate>

    suspend fun candidates(accessToken: String): Result<List<AuthorityCandidate>>

    suspend fun grantDevice(
        accessToken: String,
        chain: AuthorityChainState,
        candidate: AuthorityCandidate,
        stepUp: AuthorityStepUp,
        fingerprintConfirmed: Boolean,
    ): Result<AuthoritySubmission>

    suspend fun revokeDevice(
        accessToken: String,
        chain: AuthorityChainState,
        deviceKeyB64Url: String,
        reason: Int,
        stepUp: AuthorityStepUp,
    ): Result<AuthoritySubmission>

    suspend fun beginRecovery(recoveryArtifact: String): Result<AdoptionOffer>

    suspend fun recoverAuthority(
        accessToken: String,
        chain: AuthorityChainState,
        recoveryArtifact: String,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission>

    suspend fun beginAccountRecovery(): Result<AdoptionOffer>

    suspend fun recoverThroughAccountRecovery(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission>

    suspend fun approvals(accessToken: String): Result<List<AuthorityApproval>>

    suspend fun approve(
        accessToken: String,
        chain: AuthorityChainState,
        approval: AuthorityApproval,
    ): Result<Unit>

    suspend fun registerSecurityNotifications(
        accessToken: String,
        pushToken: String,
        platform: String,
        appId: String,
        deviceLabel: String,
    ): Result<Unit>

    suspend fun securityNotifications(accessToken: String): Result<List<SecurityNotificationView>>

    suspend fun removeSecurityNotification(
        accessToken: String,
        installationId: String,
        pin: String?,
    ): Result<Unit>

    suspend fun installationId(): String
}

/** [recoveryArtifact] is the private recovery key: show it once, never persist or log it. */
data class AdoptionOffer(
    val recoveryArtifact: String,
)
