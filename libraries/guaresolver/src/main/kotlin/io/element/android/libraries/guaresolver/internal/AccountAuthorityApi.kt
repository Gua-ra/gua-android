/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

internal interface AccountAuthorityApi {
    @POST("account/authority/challenge")
    suspend fun challenge(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityChallengeRequest,
    ): AuthorityChallengeResponse

    @POST("security/authority/step-up/start")
    suspend fun startWebStepUp(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityStepUpStartRequest,
    ): AuthorityStepUpStartResponse

    @POST("account/authority/adopt")
    suspend fun adopt(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityRecordSubmissionRequest,
    ): AuthoritySubmissionResponse

    @POST("account/authority/device/grant")
    suspend fun grantDevice(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityRecordSubmissionRequest,
    ): AuthoritySubmissionResponse

    @POST("account/authority/device/revoke")
    suspend fun revokeDevice(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityRecordSubmissionRequest,
    ): AuthoritySubmissionResponse

    @POST("account/authority/recover")
    suspend fun recoverAuthority(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityRecordSubmissionRequest,
    ): AuthoritySubmissionResponse

    @POST("account/authority/oppose")
    suspend fun oppose(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityOpposeRequest,
    )

    @POST("account/authority/oppose/record")
    suspend fun opposeWithRecord(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityRecordSubmissionRequest,
    )

    @POST("account/authority/device/candidate")
    suspend fun offerCandidate(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityCandidateRequest,
    ): AuthorityCandidateResponse

    @GET("account/authority/device/candidate")
    suspend fun candidates(
        @Header("Authorization") authorization: String,
    ): List<AuthorityCandidateResponse>

    @POST("account/security-notifications")
    suspend fun registerSecurityNotification(
        @Header("Authorization") authorization: String,
        @Body body: SecurityNotificationRegisterRequest,
    ): SecurityNotificationRegisteredResponse

    @GET("account/security-notifications")
    suspend fun securityNotifications(
        @Header("Authorization") authorization: String,
    ): List<SecurityNotificationResponse>

    @POST("account/security-notifications/remove")
    suspend fun removeSecurityNotification(
        @Header("Authorization") authorization: String,
        @Body body: SecurityNotificationRemoveRequest,
    )

    @GET("account/authority")
    suspend fun state(
        @Header("Authorization") authorization: String,
    ): AuthorityStateResponse

    @GET("account/authority/approval")
    suspend fun liveApprovals(
        @Header("Authorization") authorization: String,
    ): List<AuthorityApprovalResponse>

    @POST("account/authority/approval/{approvalId}/sign")
    suspend fun signApproval(
        @Header("Authorization") authorization: String,
        @Path("approvalId") approvalId: String,
        @Body body: AuthorityApprovalSignRequest,
    )
}

@Serializable
internal data class AuthorityChallengeRequest(
    val purpose: String,
    val pin: String? = null,
    val passkeyStepUpId: String? = null,
    val passkeyCredential: JsonElement? = null,
)

@Serializable
internal data class AuthorityStepUpStartRequest(
    val purpose: String,
    val redirectUri: String? = null,
)

@Serializable
internal data class AuthorityStepUpStartResponse(
    val stepUpUrl: String,
)

@Serializable
internal data class AuthorityChallengeResponse(
    val challenge: String,
    val expiresInSeconds: Long = 0,
)

@Serializable
internal data class AuthorityRecordSubmissionRequest(
    val record: String,
    val signature: String,
    val challenge: String,
    val recoveryArtifactConfirmed: Boolean = false,
)

@Serializable
internal data class AuthoritySubmissionResponse(
    val seq: Long = 0,
    val state: String = "PENDING",
    val effectiveAtEpochSeconds: Long = 0,
    val recordHash: String = "",
)

@Serializable
internal data class AuthorityOpposeRequest(
    val recordHash: String? = null,
    val pin: String? = null,
)

@Serializable
internal data class AuthorityStateResponse(
    val accountId: String,
    val accountClass: String = "BOOTSTRAP",
    val state: String = "BOOTSTRAP",
    val headSeq: Long = 0,
    val headHash: String = "",
    val devices: List<AuthorityDeviceResponse> = emptyList(),
    val pending: AuthorityPendingResponse? = null,
)

@Serializable
internal data class AuthorityDeviceResponse(
    val deviceKey: String,
    val label: String = "",
    val state: String = "ACTIVE",
    val quarantineUntilEpochSeconds: Long? = null,
    val grantedSeq: Long = 0,
)

@Serializable
internal data class AuthorityPendingResponse(
    val type: String,
    val seq: Long = 0,
    val effectiveAtEpochSeconds: Long = 0,
    val recordHash: String = "",
    val prevHash: String? = null,
)

@Serializable
internal data class AuthorityApprovalResponse(
    val approvalId: String,
    val code: String,
    val action: String? = null,
    val actionDigest: String,
    val challenge: String,
    val expiresAtEpochSeconds: Long = 0,
)

@Serializable
internal data class AuthorityApprovalSignRequest(
    val signature: String,
)

@Serializable
internal data class AuthorityCandidateRequest(
    val deviceKeyB64: String,
    val label: String? = null,
)

@Serializable
internal data class AuthorityCandidateResponse(
    val deviceKeyB64: String,
    val fingerprint: String = "",
    val label: String? = null,
    val expiresAtEpochSeconds: Long = 0,
)

@Serializable
internal data class SecurityNotificationRegisterRequest(
    val installationId: String,
    val platform: String,
    val token: String,
    val appId: String,
    val deviceLabel: String? = null,
    val authorityDeviceKeyB64: String? = null,
    val challenge: String? = null,
    val signature: String? = null,
)

@Serializable
internal data class SecurityNotificationRegisteredResponse(
    val installationId: String = "",
    val tokenFingerprint: String = "",
    val bound: Boolean = false,
)

@Serializable
internal data class SecurityNotificationResponse(
    val installationId: String,
    val platform: String = "",
    val deviceLabel: String? = null,
    val tokenFingerprint: String = "",
    val boundToAnAuthorityDevice: Boolean = false,
    val lastSeenAtEpochSeconds: Long = 0,
)

@Serializable
internal data class SecurityNotificationRemoveRequest(
    val installationId: String,
    val pin: String? = null,
    val challenge: String? = null,
    val signature: String? = null,
)
