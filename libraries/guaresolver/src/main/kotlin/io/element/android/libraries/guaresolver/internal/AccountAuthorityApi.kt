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

    /**
     * The web step-up handoff of ADM-009 decision 4 step 2.
     *
     * Under `security/`, not `account/authority/`, because it is the same handoff the two factor-enrollment
     * starts use and the server builds all three from one session builder. It answers 503
     * `authority_disabled` like every other call here, and 409 `authority_step_up_unavailable` for an account
     * whose page would have nothing to ask for.
     */
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

    /**
     * The signed objection of ADM-009 decision 2. A separate path from `oppose` because it is a different
     * thing: that one is a session saying no to an adoption, this one is a device key the chain has active
     * saying no to anything else, and the server accepts each only where it is permitted.
     */
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

    // GUA FORK: the security notification channel of ADM-009 gate 2. Its own path prefix and its own
    // off-by-default flag on the server, because it is the only part of this feature that holds a push
    // credential and a per-install identifier.

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

/**
 * The step-up travels with the challenge request, so the step-up can never be older than the challenge it
 * authorizes. There is no field for a phone code and no field for saying a factor is unavailable on this
 * device: a claim that a factor cannot be produced costs an attacker nothing.
 */
@Serializable
internal data class AuthorityChallengeRequest(
    val purpose: String,
    val pin: String? = null,
    /** Id from `POST /security/passkey/stepup/options`, sent with the assertion below. */
    val passkeyStepUpId: String? = null,
    /** The user-verifying passkey assertion, which settles the step-up on its own. */
    val passkeyCredential: JsonElement? = null,
)

/**
 * What a web step-up start carries, and it is two fields.
 *
 * There is no field for a phone number, because no arm of that page sends a code, and none for saying which
 * factor this device can produce: a claim that a factor is unavailable costs an attacker nothing and could
 * only ever ask for something weaker.
 */
@Serializable
internal data class AuthorityStepUpStartRequest(
    val purpose: String,
    /** The app scheme this build answers, checked against the deployment's own allowlist. */
    val redirectUri: String? = null,
)

/** One field: what the sheet leaves behind is server state, so there is nothing for this client to carry. */
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

/**
 * One install's security-notification destination.
 *
 * The installation id is the upsert key and the whole point of the row: it is client-generated, sealed on the
 * device rather than kept in preferences, and stable across sign-out, which is what lets the registration
 * outlive the sessions a completed account recovery ends.
 */
@Serializable
internal data class SecurityNotificationRegisterRequest(
    val installationId: String,
    val platform: String,
    val token: String,
    val appId: String,
    val deviceLabel: String? = null,
    /** Present only when this install holds an authority key, and then with the challenge and signature. */
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

/**
 * A removal, whose tier the server decides from what the caller can produce and never from a field it set.
 *
 * Naming your own install in both ids is the tier that needs nothing else. Naming another install needs a
 * factor past the fresh-factor hold, and a device signature where the row carries a key, which is what stops
 * an attacker holding a just-minted PIN emptying the channel before starting a transition.
 */
@Serializable
internal data class SecurityNotificationRemoveRequest(
    val installationId: String,
    val callerInstallationId: String? = null,
    val pin: String? = null,
    val challenge: String? = null,
    val signature: String? = null,
)
