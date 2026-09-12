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

/**
 * GUA FORK: Retrofit surface for the Gua identity-service `POST /directory/lookup` contact-discovery
 * endpoint. Internal to the module; the public API only ever exposes
 * [io.element.android.libraries.guaresolver.ContactMatch].
 *
 * The request carries **hashed** phone digests only (see
 * [io.element.android.libraries.guaresolver.PhoneHasher]) and is authenticated with the caller's
 * Matrix access token, mirroring iOS' `lookupContacts(accessToken:phones:)`.
 */
internal interface IdentityServiceApi {
    @POST("directory/lookup")
    suspend fun lookupContacts(
        @Header("Authorization") authorization: String,
        @Body body: LookupRequest,
    ): LookupResponse

    // GUA FORK: Two-step verification (account PIN). Mirrors iOS' `security/pin*` endpoints.

    @GET("security/pin/status")
    suspend fun pinStatus(
        @Header("Authorization") authorization: String,
    ): PinStatusResponse

    @POST("security/pin")
    suspend fun setInitialPin(
        @Header("Authorization") authorization: String,
        @Body body: SetInitialPinRequest,
    )

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

    // GUA FORK: change phone number, against the real `/account` contract.
    //
    // An earlier revision of this file called `security/pin/reauth`, `otp/change-number/request` and
    // `otp/change-number`. The identity service has never served any of those three, so every phone
    // change failed at the first call. The real sequence is:
    //   1. account/reauth/start   sends an OTP to the CURRENT number (proof of possession only),
    //   2. account/reauth/verify  exchanges that OTP for a single-use, PHONE_CHANGE-scoped token,
    //   3. account/phone/change/start   spends the token AND a step-up factor (a passkey assertion,
    //      else the account PIN), and only then sends the OTP to the NEW number,
    //   4. account/phone/change/complete   redeems the challenge with that OTP.
    // No SMS reaches the new number before step 3 has accepted a step-up factor.

    @POST("account/reauth/start")
    suspend fun startAccountReauth(
        @Header("Authorization") authorization: String,
        @Header("Accept-Language") acceptLanguage: String?,
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

    // GUA FORK: passkey enrollment. Returns the authenticated web-ceremony URL the client opens to
    // complete WebAuthn registration at the IdP.
    //
    // POST, not GET: the identity service maps this as @PostMapping("/passkey/enroll/start"), and
    // iOS reaches it through a helper that always sends POST. An earlier comment here described it
    // as a GET, which is what the declaration was written to match, so every tap was answered with
    // 405 before any ceremony could start. The request carries no body; the access token in the
    // Authorization header is the whole input.
    @POST("security/passkey/enroll/start")
    suspend fun startPasskeyEnrollment(
        @Header("Authorization") authorization: String,
    ): PasskeyEnrollStartResponse

    // GUA FORK: account genesis registration (ADM-008 decision 6, step 1). Deliberately unauthenticated:
    // it runs before any OIDC flow exists to authenticate against, and the body carries its own
    // possession proof under the key committed inside the genesis itself.

    @POST("account/genesis")
    suspend fun registerAccountGenesis(
        @Body body: AccountGenesisRegisterRequest,
    ): AccountGenesisRegisterResponse
}

@Serializable
internal data class LookupRequest(
    /** Hashed phone digests — never raw numbers. */
    val hashedPhones: List<String>,
)

@Serializable
internal data class LookupMatch(
    /** Echoes back the submitted hashed digest so the client can map the hit onto the address book. */
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
    /**
     * Remaining seconds of the fresh-2FA hold before the account PIN may be spent as the
     * phone-change step-up (0 = no active hold). Defaults to 0 for older identity-service builds
     * that do not yet return the field.
     */
    val changePhoneCooldownRemainingSeconds: Long = 0,
    /** True when the account has a passkey registered and this deployment has passkeys enabled. */
    val passkeyRegistered: Boolean = false,
    /** The strongest factor the account holds: "PASSKEY", "PIN" or "PHONE_OTP". */
    val preferredFactor: String? = null,
    /**
     * The factors `account/phone/change/start` accepts as its step-up, strongest first. Absent on
     * builds that predate the factor policy; the client then derives the list from the factors the
     * account is known to hold rather than assuming the account can settle nothing.
     */
    val phoneChangeStepUpFactors: List<String> = emptyList(),
)

@Serializable
internal data class SetInitialPinRequest(
    val userId: String,
    val newPin: String,
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
internal data class AccountReauthVerifyRequest(
    /** OTP delivered by SMS to the number currently on file. */
    val code: String,
    /**
     * The privileged operation the issued token may be spent on. The server binds the token to it
     * and refuses to spend a token scoped elsewhere, so this is never left to the default.
     */
    val operation: String,
)

@Serializable
internal data class AccountReauthTokenResponse(
    val reauthToken: String,
    val expiresInSeconds: Long? = null,
)

@Serializable
internal data class PhoneChangeStartRequest(
    /** Single-use, PHONE_CHANGE-scoped token from `account/reauth/verify`. Spent by this call. */
    val reauthToken: String,
    val newPhone: String,
    /**
     * Account PIN, the fallback step-up factor. Sent when the PIN is the factor being offered; not
     * consulted by the server when a passkey assertion is accepted, since that is the stronger one.
     */
    val pin: String? = null,
    /** Step-up ceremony id from `security/passkey/stepup/options`, sent with [passkeyCredential]. */
    val passkeyStepUpId: String? = null,
    /** Assertion response JSON from the step-up WebAuthn ceremony, verbatim. */
    val passkeyCredential: JsonElement? = null,
)

@Serializable
internal data class PhoneChangeStartResponse(
    /** Opaque challenge proving the step-up succeeded and an OTP went to the new number. */
    val challengeId: String,
    val otpExpiresInSeconds: Long? = null,
)

@Serializable
internal data class PhoneChangeCompleteRequest(
    val challengeId: String,
    /** OTP delivered by SMS to the NEW number. */
    val code: String,
)

@Serializable
internal data class AccountGenesisRegisterRequest(
    /** Canonical AccountGenesis bytes (87 bytes), base64url without padding. */
    val genesis: String,
    /**
     * Ed25519 signature by the authority key over the ASCII domain "gua-account-genesis-proof.v1"
     * followed by the canonical bytes, base64url without padding.
     */
    val proof: String,
)

@Serializable
internal data class AccountGenesisRegisterResponse(
    /** The accountId the server derived from the bytes it received. */
    val accountId: String,
    /** Single-use handle to send as login_hint="gua:phone=<E.164>;genesis=<handle>". */
    val attachHandle: String,
    /** When the handle stops being attachable, ISO-8601. Absent on builds that do not send it. */
    val expiresAt: String? = null,
)

@Serializable
internal data class PasskeyEnrollStartResponse(
    /** Authenticated web-ceremony URL to open at the IdP to complete passkey registration. */
    val enrollUrl: String,
)

/**
 * Identity-service error envelope. The typed [io.element.android.libraries.guaresolver.ResolverError]
 * PIN cases are derived from the `code` field, mirroring iOS' `ErrorBody.code` handling.
 */
@Serializable
internal data class IdentityServiceErrorBody(
    val code: String? = null,
    val message: String? = null,
    val error: String? = null,
    /** Present on `twofa_cooldown_active`: seconds the caller must wait before retrying. */
    val retryAfterSeconds: Long? = null,
)
