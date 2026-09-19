/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * GUA FORK: Retrofit surface for the identity-service account authority chain (ADM-009). Internal to the
 * module; the public API is
 * [io.element.android.libraries.guaresolver.authority.AccountAuthorityClient].
 *
 * Every endpoint is bearer-authenticated and every one answers 503 `authority_disabled` until a deployment
 * turns the feature on.
 *
 * **No request here carries a phone code**, and none may be given one: the phone is a notification channel
 * and authorizes nothing in this chain (ADM-009 decision 9).
 */
internal interface AccountAuthorityApi {
    @POST("account/authority/challenge")
    suspend fun challenge(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityChallengeRequest,
    ): AuthorityChallengeResponse

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

    @POST("account/authority/oppose")
    suspend fun oppose(
        @Header("Authorization") authorization: String,
        @Body body: AuthorityOpposeRequest,
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

/**
 * The step-up travels with the challenge request, so the step-up can never be older than the challenge it
 * authorizes. There is no field for a phone code and no field for saying a factor is unavailable on this
 * device: a claim that a factor cannot be produced costs an attacker nothing.
 */
@Serializable
internal data class AuthorityChallengeRequest(
    val purpose: String,
    val pin: String? = null,
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
    /** The server stores only the challenge's SHA-256, so it cannot rebuild the preimage without this. */
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
    /** Second and later oppositions: a step-up on any factor the account holds, at any age. */
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
