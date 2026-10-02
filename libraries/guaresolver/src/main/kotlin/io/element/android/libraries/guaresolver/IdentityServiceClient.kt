/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * Client for the Gua identity service: contact discovery, two-step verification factors, phone
 * number change and account genesis registration.
 *
 * Every method fails with a [ResolverError], notably [ResolverError.NotConfigured] when no
 * identity-service URL is configured.
 *
 * A bearer session alone must never add a durable factor: every factor enrollment runs as an
 * authenticated web ceremony at the IdP.
 */
interface IdentityServiceClient {
    /**
     * Looks up which of the supplied hashed phone numbers belong to a Gua account
     * (`POST /directory/lookup`), authenticated with the caller's Matrix access token.
     *
     * [hashedPhones] must already be protected with [PhoneHasher.hash]. Raw numbers must never be passed
     * here. Only the contacts that are on Gua and discoverable come back.
     */
    suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>>

    /**
     * The factors the account holds, per the server (`GET /security/pin/status`). Every factor decision
     * branches on this, never on a lone `hasPin`: an account with a passkey and no PIN already has
     * two-step verification.
     *
     * A failure means the factors are unknown. Callers must not read it as "no factors".
     */
    suspend fun accountFactorStatus(accessToken: String, userId: String): Result<AccountFactorStatus>

    /**
     * Cancels a live delayed account recovery on the caller's own account
     * (`POST /security/recovery/cancel`). Succeeds whether or not one was live, so a retry is safe.
     */
    suspend fun cancelAccountRecovery(accessToken: String): Result<Unit>

    /**
     * Starts enrollment of the account's first PIN (`POST /security/pin/enroll/start`) and returns the
     * authenticated web-ceremony URL to open at the IdP. The step-up that proves the account holder is
     * present runs in that web session.
     *
     * The call names this build's [EnrollmentRedirectProvider] redirect so the ceremony returns to the
     * app it was opened from. Fails with [ResolverError.PinAlreadySet] when the account already holds a PIN.
     */
    suspend fun startPinEnrollment(accessToken: String): Result<String>

    /**
     * Starts an OTP-protected PIN change: verifies the current PIN and triggers an OTP to [phone].
     * Returns the challenge id. Fails with [ResolverError.InvalidPin], [ResolverError.PinLocked] or
     * [ResolverError.PinChangeCooldown].
     */
    suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String>

    /**
     * Completes a PIN change with the OTP code and the new PIN. Fails with [ResolverError.InvalidOtp] or
     * [ResolverError.PinChangeChallengeInvalid].
     */
    suspend fun completePinChange(accessToken: String, challengeId: String, otpCode: String, newPin: String): Result<Unit>

    // Change phone number. Ordering is the security property: the OTP to the NEW number is sent by
    // [startPhoneChange], which the server only reaches after it has spent the reauth token and accepted
    // a step-up factor. Nothing before that point can text the new number.

    /**
     * Sends a reauthentication OTP to the number currently on file (`POST /account/reauth/start`).
     * [language] is an optional BCP-47 tag that localises the message.
     *
     * [phone] is the number the signed-in user typed as their current one. The server texts it only when
     * it matches the account's own binding, so this call can never send an SMS to a number the account
     * does not hold. It proves possession of the current number and nothing more.
     *
     * Fails with [ResolverError.ReauthPhoneMismatch] when the number is not this account's,
     * [ResolverError.InvalidPhoneNumber] when it is not a phone number, or [ResolverError.RateLimited]
     * once the per-account attempt cap is reached.
     */
    suspend fun startPhoneChangeReauth(accessToken: String, phone: String, language: String?): Result<Unit>

    /**
     * Exchanges the reauth OTP for a single-use token scoped to the phone-change operation
     * (`POST /account/reauth/verify`). No SMS is sent here. [phone] is sent again because the server
     * keeps nothing between the two calls.
     *
     * Fails with [ResolverError.InvalidOtp] and the same refusals as [startPhoneChangeReauth].
     */
    suspend fun verifyPhoneChangeReauth(accessToken: String, phone: String, code: String): Result<String>

    /**
     * Starts the phone-number change (`POST /account/phone/change/start`): spends [reauthToken] and a
     * step-up factor, and only then sends an OTP to [newPhone]. Returns the challenge to redeem at
     * [completePhoneChange].
     *
     * The server spends [reauthToken] before it weighs the step-up factor, so the token is gone on every
     * outcome. Callers mint a fresh one through [startPhoneChangeReauth] and never retry the same token.
     *
     * [pin] is the fallback factor. A verified passkey assertion is the preferred one and settles the
     * step-up on its own, see [passkeyStepUpId].
     *
     * Fails with [ResolverError.StepUpRequired] when the account holds neither factor (a hard block),
     * [ResolverError.InvalidPin], [ResolverError.TwoFactorCooldown], [ResolverError.PhoneChangeCooldown],
     * [ResolverError.PhoneAlreadyLinked] or [ResolverError.InvalidReauthToken].
     */
    suspend fun startPhoneChange(
        accessToken: String,
        reauthToken: String,
        newPhone: String,
        pin: String?,
        passkeyStepUpId: String?,
        passkeyCredentialJson: String?,
        language: String?,
    ): Result<PhoneChangeChallenge>

    /**
     * Completes the phone-number change (`POST /account/phone/change/complete`): redeems the challenge
     * from [startPhoneChange] with the OTP delivered to the new number. Fails with
     * [ResolverError.InvalidOtp], [ResolverError.PhoneChangeChallengeInvalid] or
     * [ResolverError.PhoneAlreadyLinked].
     */
    suspend fun completePhoneChange(accessToken: String, challengeId: String, code: String): Result<Unit>

    /**
     * Starts passkey enrollment (`POST /security/passkey/enroll/start`) and returns the authenticated
     * web-ceremony URL to open at the IdP. The URL carries a short-lived enrollment token, so the client
     * just opens it in a Custom Tab and the user completes the WebAuthn registration there. Names this
     * build's [EnrollmentRedirectProvider] redirect like [startPinEnrollment].
     */
    suspend fun startPasskeyEnrollment(accessToken: String): Result<String>

    /**
     * Registers an `AccountGenesis` generated on this device (`POST /account/genesis`) and returns its
     * accountId plus a single-use attach handle. Registering creates no account and attaches nothing.
     *
     * Unauthenticated by design: it runs before any login session exists, and [proofB64Url] is a
     * signature under the authority key committed in [genesisB64Url]. Both are base64url without padding.
     *
     * A [ResolverError.Server] with status 503 means this deployment does not do account genesis. The
     * caller then continues the signup unchanged rather than surface an error.
     */
    suspend fun registerAccountGenesis(genesisB64Url: String, proofB64Url: String): Result<AccountGenesisRegistration>
}

/**
 * What `POST /account/phone/change/start` returned once the step-up was accepted and the OTP went
 * out to the new number. `complete` redeems it.
 */
data class PhoneChangeChallenge(
    val challengeId: String,
    /** Seconds before the new-number OTP expires, or null when the server did not say. */
    val otpExpiresInSeconds: Long?,
)

/**
 * What `POST /account/genesis` returned. The handle is a routing hint, not a capability: a stolen one
 * attaches nothing and a planted one fails at the proof step.
 */
data class AccountGenesisRegistration(
    val accountId: String,
    val attachHandle: String,
)
