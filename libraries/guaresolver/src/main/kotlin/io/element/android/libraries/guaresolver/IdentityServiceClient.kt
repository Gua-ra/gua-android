/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: the Gua identity-service client: contact discovery, two-step verification factors,
 * phone number changes and account genesis. Android counterpart of iOS `IdentityServiceClientProtocol`.
 *
 * PRIVACY: callers pass hashed phone digests, never raw numbers, see [PhoneHasher]. The address
 * book is never persisted; the digests are sent over TLS for a one-shot lookup and the raw numbers
 * never leave the device. The digest is a privacy-hardening step, not a guarantee of
 * irreversibility: the domain tag is public and the phone keyspace is small, so the identity-service
 * can match a digest back to a number. Only the contacts that are on Gua and discoverable come back.
 *
 * SECURITY: a bearer session on its own must never add a durable factor. Every factor enrollment
 * runs as an authenticated web ceremony at the IdP, the only place a step-up (passkey, else PIN,
 * else current number plus OTP) can be asked for on every platform.
 */
interface IdentityServiceClient {
    /**
     * Look up which of the supplied hashed phone numbers belong to a Gua account.
     *
     * @param accessToken the caller's Matrix access token (the lookup is authenticated, mirroring iOS).
     * @param hashedPhones address-book phone numbers already protected via [PhoneHasher.hash]
     * (E.164 -> stable, domain-tagged SHA-256 digest; the tag is public, not a salt). Raw numbers
     * must never be passed here.
     * @return [Result.success] with the matched [ContactMatch]es, or [Result.failure] with a
     * [ResolverError] (notably [ResolverError.NotConfigured] when no identity-service URL is configured).
     */
    suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>>

    // GUA FORK: Two-step verification factors. Android counterpart of iOS
    // `IdentityServiceClientProtocol.accountFactorStatus / startPinEnrollment / startPinChange /
    // completePinChange`.

    /**
     * The factors the account holds, per the server (`GET /security/pin/status`): whether a PIN is
     * set, whether a passkey is registered, which factor to offer first, which ones a phone change
     * accepts, and any remaining fresh-2FA hold on the PIN.
     *
     * Every factor decision branches on this, never on a lone `hasPin`: an account with a passkey
     * and no PIN already has two-step verification.
     *
     * @return [Result.success] with the [AccountFactorStatus], or [Result.failure] with a
     * [ResolverError]. A failure means the factors are UNKNOWN, not "no factors".
     */
    suspend fun accountFactorStatus(accessToken: String, userId: String): Result<AccountFactorStatus>

    /**
     * Cancel a live delayed account recovery on the caller's own account
     * (`POST /security/recovery/cancel`). Succeeds whether or not one was live, so a retry is safe.
     * The server also counts it as account activity, so whoever started the recovery cannot simply
     * start another one straight away.
     *
     * @return [Result.success] on success, or [Result.failure] with a [ResolverError].
     */
    suspend fun cancelAccountRecovery(accessToken: String): Result<Unit>

    /**
     * Start enrollment of the account's FIRST PIN (`POST /security/pin/enroll/start`) and return the
     * authenticated web-ceremony URL to open at the IdP, as [startPasskeyEnrollment] does for a
     * passkey. There is no native path: the old `POST /security/pin` answers 403 for everyone (see
     * the interface header). Changing an existing PIN is unaffected, since that flow proves the
     * current PIN first.
     *
     * The call sends this build's own [EnrollmentRedirectProvider] redirect so the ceremony returns
     * to the app it was opened from; a deployment that refuses it is asked again without one.
     *
     * @return [Result.success] with the enrollment URL, or [Result.failure] with a [ResolverError]
     * (notably [ResolverError.PinAlreadySet] when the account already holds a PIN).
     */
    suspend fun startPinEnrollment(accessToken: String): Result<String>

    /**
     * Start an OTP-protected PIN change: verifies the current PIN and triggers an OTP to [phone].
     * Mirrors iOS `startPinChange`.
     *
     * @return [Result.success] with the challenge id, or [Result.failure] with a [ResolverError]
     * (e.g. [ResolverError.InvalidPin], [ResolverError.PinLocked], [ResolverError.PinChangeCooldown]).
     */
    suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String>

    /**
     * Complete a PIN change with the OTP code and the new PIN. Mirrors iOS `completePinChange`.
     *
     * @return [Result.success] on success, or [Result.failure] with a [ResolverError] (e.g.
     * [ResolverError.InvalidOtp], [ResolverError.PinChangeChallengeInvalid]).
     */
    suspend fun completePinChange(accessToken: String, challengeId: String, otpCode: String, newPin: String): Result<Unit>

    // GUA FORK: change phone number. Android counterpart of iOS
    // `IdentityServiceClientProtocol.startPhoneChangeReauth / verifyPhoneChangeReauth /
    // startPhoneChange / completePhoneChange`. The endpoint sequence is documented once, on
    // `IdentityServiceApi`. Two invariants hold it together: nothing is sent to the NEW number before
    // [startPhoneChange] has spent the reauth token and accepted a step-up factor, and
    // `step_up_required` is a hard block with no token-only fallback.

    /**
     * Send a reauthentication OTP to the number currently on file (`POST /account/reauth/start`),
     * the first half of the single-use reauth token. The optional BCP-47 [language] tag localises
     * the message.
     *
     * [phone] is the number the signed-in user typed as their current one. The server never hands
     * the number out; it digests the submitted one, compares it against the account's binding and
     * texts it only on a match, so this call cannot send an SMS to a number the account does not hold.
     *
     * This proves only possession of the current number, so it is not sufficient on its own: a
     * SIM-swap attacker holds that number too. The step-up factor [startPhoneChange] demands is the
     * other half.
     *
     * @return [Result.success] on success, or [Result.failure] with a [ResolverError]:
     * [ResolverError.ReauthPhoneMismatch] when the number is not this account's (surfaced without
     * suggesting whose it might be), [ResolverError.InvalidPhoneNumber], or [ResolverError.RateLimited].
     */
    suspend fun startPhoneChangeReauth(accessToken: String, phone: String, language: String?): Result<Unit>

    /**
     * Exchange the reauth OTP for a single-use token scoped to the phone-change operation
     * (`POST /account/reauth/verify`). No SMS is sent here.
     *
     * [phone] is sent again because the server keeps nothing between the two calls.
     *
     * @return [Result.success] with the opaque reauth token, or [Result.failure] with a
     * [ResolverError] (notably [ResolverError.InvalidOtp] and the same refusals as
     * [startPhoneChangeReauth]).
     */
    suspend fun verifyPhoneChangeReauth(accessToken: String, phone: String, code: String): Result<String>

    /**
     * Start the phone-number change (`POST /account/phone/change/start`): spends [reauthToken] and a
     * step-up factor, and only then sends an OTP to [newPhone]. Returns the challenge to redeem at
     * [completePhoneChange].
     *
     * The server spends [reauthToken] before it weighs the step-up factor, so the token is gone on
     * every outcome; a retry needs a fresh one from [startPhoneChangeReauth]. A verified passkey
     * assertion ([passkeyStepUpId], [passkeyCredentialJson]) is the preferred factor; [pin] is the
     * fallback when the account holds one.
     *
     * @return [Result.success] with the [PhoneChangeChallenge], or [Result.failure] with a
     * [ResolverError]: [ResolverError.StepUpRequired] when the account holds neither factor (a hard
     * block), [ResolverError.InvalidPin], [ResolverError.TwoFactorCooldown],
     * [ResolverError.PhoneChangeCooldown], [ResolverError.PhoneAlreadyLinked],
     * [ResolverError.InvalidReauthToken].
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
     * Complete the phone-number change (`POST /account/phone/change/complete`): redeems the
     * challenge from [startPhoneChange] with the OTP delivered to the new number.
     *
     * @return [Result.success] on success, or [Result.failure] with a [ResolverError] (e.g.
     * [ResolverError.InvalidOtp], [ResolverError.PhoneChangeChallengeInvalid],
     * [ResolverError.PhoneAlreadyLinked]).
     */
    suspend fun completePhoneChange(accessToken: String, challengeId: String, code: String): Result<Unit>

    // GUA FORK: Passkey enrollment. Android counterpart of iOS
    // `IdentityServiceClientProtocol.startPasskeyEnrollment`.

    /**
     * Start passkey enrollment (`POST /security/passkey/enroll/start`) and return the authenticated
     * web-ceremony URL to open at the IdP. Mirrors iOS `startPasskeyEnrollment(accessToken:)`.
     *
     * The URL carries a short-lived enrollment token, so the client opens it in a Chrome Custom Tab
     * and the user completes the WebAuthn registration in the browser. Like [startPinEnrollment] it
     * sends this build's own [EnrollmentRedirectProvider] redirect and retries without one when the
     * deployment refuses it.
     *
     * @return [Result.success] with the enrollment URL, or [Result.failure] with a [ResolverError].
     */
    suspend fun startPasskeyEnrollment(accessToken: String): Result<String>

    // GUA FORK: account genesis (ADM-008 Phase 3).

    /**
     * Register an `AccountGenesis` generated on this device and obtain its accountId plus a single-use
     * attach handle (`POST /account/genesis`).
     *
     * Unauthenticated by design: it runs before any login session exists, and [proofB64Url] is what
     * makes it self-authenticating, being a signature under the authority key committed inside
     * [genesisB64Url] itself. Registering creates no account and attaches nothing.
     *
     * @param genesisB64Url the canonical genesis bytes, base64url without padding.
     * @param proofB64Url the registration proof, base64url without padding.
     * @return [Result.success] with the registration, or [Result.failure] with a [ResolverError].
     * A [ResolverError.Server] with status 503 means this deployment does not do account genesis, and
     * the caller must continue with today's signup unchanged rather than surface an error.
     */
    suspend fun registerAccountGenesis(genesisB64Url: String, proofB64Url: String): Result<AccountGenesisRegistration>
}

/**
 * GUA FORK: what `POST /account/phone/change/start` returned once the step-up was accepted and the
 * OTP went out to the new number. The challenge is the proof that both happened; it is what
 * `complete` redeems.
 */
data class PhoneChangeChallenge(
    val challengeId: String,
    /** Seconds before the new-number OTP expires, or null when the server did not say. */
    val otpExpiresInSeconds: Long?,
)

/**
 * GUA FORK: what `POST /account/genesis` returned. The handle is a routing hint, not a capability: a
 * stolen one attaches nothing and a planted one fails at the proof step.
 */
data class AccountGenesisRegistration(
    val accountId: String,
    val attachHandle: String,
)
