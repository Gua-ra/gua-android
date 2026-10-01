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

internal interface IdentityServiceApi {
    @POST("directory/lookup")
    suspend fun lookupContacts(
        @Header("Authorization") authorization: String,
        @Body body: LookupRequest,
    ): LookupResponse

    @GET("security/pin/status")
    suspend fun pinStatus(
        @Header("Authorization") authorization: String,
    ): PinStatusResponse

    // `POST /security/pin` answers 403 for every caller: the first PIN is enrolled through the web ceremony.
    @POST("security/pin/enroll/start")
    suspend fun startPinEnrollment(
        @Header("Authorization") authorization: String,
        @Body body: FactorEnrollStartRequest,
    ): FactorEnrollStartResponse

    @POST("security/pin/change/start")
    suspend fun startPinChange(
        @Header("Authorization") authorization: String,
        @Body body: StartPinChangeRequest,
    ): StartPinChangeResponse

    @POST("security/pin/change/complete")
    suspend fun completePinChange(
        @Header("Authorization") authorization: String,
        @Body body: CompletePinChangeRequest,
    )

    // Answers 204 whether or not a recovery was live.
    @POST("security/recovery/cancel")
    suspend fun cancelAccountRecovery(
        @Header("Authorization") authorization: String,
    )

    // Phone change: reauth/start, reauth/verify, phone/change/start, phone/change/complete.
    // No SMS reaches the new number before phone/change/start has accepted a step-up factor.

    @POST("account/reauth/start")
    suspend fun startAccountReauth(
        @Header("Authorization") authorization: String,
        @Header("Accept-Language") acceptLanguage: String?,
        @Body body: AccountReauthStartRequest,
    )

    @POST("account/reauth/verify")
    suspend fun verifyAccountReauth(
        @Header("Authorization") authorization: String,
        @Body body: AccountReauthVerifyRequest,
    ): AccountReauthTokenResponse

    @POST("account/phone/change/start")
    suspend fun startPhoneChange(
        @Header("Authorization") authorization: String,
        @Header("Accept-Language") acceptLanguage: String?,
        @Body body: PhoneChangeStartRequest,
    ): PhoneChangeStartResponse

    @POST("account/phone/change/complete")
    suspend fun completePhoneChange(
        @Header("Authorization") authorization: String,
        @Body body: PhoneChangeCompleteRequest,
    )

    @POST("security/passkey/enroll/start")
    suspend fun startPasskeyEnrollment(
        @Header("Authorization") authorization: String,
        @Body body: FactorEnrollStartRequest,
    ): FactorEnrollStartResponse

    // Unauthenticated: the body carries its own possession proof.

    @POST("account/genesis")
    suspend fun registerAccountGenesis(
        @Body body: AccountGenesisRegisterRequest,
    ): AccountGenesisRegisterResponse
}

@Serializable
internal data class LookupRequest(
    /** Hashed phone digests, never raw numbers. */
    val hashedPhones: List<String>,
)

@Serializable
internal data class LookupMatch(
    val hashedPhone: String,
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
internal data class LookupResponse(
    val matches: List<LookupMatch> = emptyList(),
)

@Serializable
internal data class PinStatusResponse(
    val hasPin: Boolean,
    /** Defaults to 0 for identity-service builds that do not return the field. */
    val changePhoneCooldownRemainingSeconds: Long = 0,
    val passkeyRegistered: Boolean = false,
    /** The strongest factor the account holds: "PASSKEY", "PIN" or "PHONE_OTP". */
    val preferredFactor: String? = null,
    /** Absent on builds that predate the factor policy. */
    val phoneChangeStepUpFactors: List<String> = emptyList(),
    /** The three recovery fields are absent on builds that predate delayed recovery. */
    val accountRecoveryPending: Boolean = false,
    val accountRecoveryCompletableAtEpochSeconds: Long? = null,
    val accountRecoveryExpiresAtEpochSeconds: Long? = null,
)

@Serializable
internal data class StartPinChangeRequest(
    val phone: String,
    val currentPin: String,
)

@Serializable
internal data class StartPinChangeResponse(
    val challengeId: String,
    val expiresInSeconds: Int? = null,
)

@Serializable
internal data class CompletePinChangeRequest(
    val challengeId: String,
    val otpCode: String,
    val newPin: String,
)

@Serializable
internal data class AccountReauthStartRequest(
    val phone: String,
)

@Serializable
internal data class AccountReauthVerifyRequest(
    val phone: String,
    val code: String,
    /** The server binds the token to this operation, so it is never left to the default. */
    val operation: String,
)

@Serializable
internal data class AccountReauthTokenResponse(
    val reauthToken: String,
    val expiresInSeconds: Long? = null,
)

@Serializable
internal data class PhoneChangeStartRequest(
    val reauthToken: String,
    val newPhone: String,
    val pin: String? = null,
    val passkeyStepUpId: String? = null,
    /** Assertion response JSON from the step-up WebAuthn ceremony, verbatim. */
    val passkeyCredential: JsonElement? = null,
)

@Serializable
internal data class PhoneChangeStartResponse(
    val challengeId: String,
    val otpExpiresInSeconds: Long? = null,
)

@Serializable
internal data class PhoneChangeCompleteRequest(
    val challengeId: String,
    val code: String,
)

@Serializable
internal data class AccountGenesisRegisterRequest(
    /** Canonical AccountGenesis bytes, base64url without padding. */
    val genesis: String,
    /** Registration proof, base64url without padding. */
    val proof: String,
)

@Serializable
internal data class AccountGenesisRegisterResponse(
    val accountId: String,
    val attachHandle: String,
    /** ISO-8601. Absent on builds that do not send it. */
    val expiresAt: String? = null,
)

/** A null [redirectUri] is left out of the JSON, so the server keeps its configured default. */
@Serializable
internal data class FactorEnrollStartRequest(
    val redirectUri: String? = null,
)

@Serializable
internal data class FactorEnrollStartResponse(
    val enrollUrl: String,
)

@Serializable
internal data class IdentityServiceErrorBody(
    val code: String? = null,
    val message: String? = null,
    val error: String? = null,
    /** Present on `twofa_cooldown_active`: seconds the caller must wait before retrying. */
    val retryAfterSeconds: Long? = null,
)
