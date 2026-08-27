/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: talks to the Gua identity-service (`POST /directory/lookup`) for contact discovery —
 * matching a batch of address-book phone numbers against Gua accounts. Android counterpart of iOS
 * `IdentityServiceClientProtocol.lookupContacts` (+ `ContactMatch`).
 *
 * PRIVACY: callers pass **protected (hashed) phone digests**, never raw numbers — see [PhoneHasher].
 * The address book is never persisted; the digests are sent over TLS for a one-shot lookup and the
 * raw numbers never leave the device. Only the contacts that are on Gua and discoverable come back.
 */
interface IdentityServiceClient {
    /**
     * Look up which of the supplied hashed phone numbers belong to a Gua account.
     *
     * @param accessToken the caller's Matrix access token (the lookup is authenticated, mirroring iOS).
     * @param hashedPhones address-book phone numbers already protected via [PhoneHasher.hash]
     * (E.164 -> stable, salted digest). Raw numbers must never be passed here.
     * @return [Result.success] with the matched [ContactMatch]es, or [Result.failure] with a
     * [ResolverError] (notably [ResolverError.NotConfigured] when no identity-service URL is configured).
     */
    suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>>

    // GUA FORK: Two-step verification (account PIN). Android counterpart of iOS
    // `IdentityServiceClientProtocol.pinStatus / setInitialPin / startPinChange / completePinChange`.

    /**
     * The account two-step-verification (PIN) status, including the change-phone fresh-2FA cooldown.
     *
     * @param accessToken the caller's access token, sent as the bearer credential.
     * @param userId the caller's Matrix id (mirrors iOS, which scopes the status to the user).
     * @return [Result.success] with the [PinStatus] (whether a PIN is set and any remaining
     * change-phone cooldown), or [Result.failure] with a [ResolverError].
     */
    suspend fun pinStatus(accessToken: String, userId: String): Result<PinStatus>

    /**
     * Set the initial account PIN (no existing PIN). Mirrors iOS `setInitialPin`.
     *
     * @return [Result.success] on success, or [Result.failure] with a [ResolverError] (notably
     * [ResolverError.InvalidPin] when the PIN is rejected).
     */
    suspend fun setInitialPin(accessToken: String, userId: String, newPin: String): Result<Unit>

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

    // GUA FORK: Change phone number (PIN-first). Android counterpart of iOS
    // `IdentityServiceClientProtocol.verifyPinReauth / requestPhoneChangeOtp / changePhoneNumber`.
    // The PIN step-up runs FIRST and yields a short-lived reauth token; the SMS is only sent from
    // [requestPhoneChangeOtp] and the token gates both the request and the completion.

    /**
     * Verify the account PIN as an up-front step-up and obtain a short-lived reauth token. Mirrors
     * iOS `verifyPinReauth`. No SMS is sent here.
     *
     * @return [Result.success] with the opaque reauth token, or [Result.failure] with a
     * [ResolverError] (notably [ResolverError.InvalidPin], [ResolverError.PinLocked]).
     */
    suspend fun verifyPinReauth(accessToken: String, userId: String, pin: String): Result<String>

    /**
     * Request an OTP to be sent to the [newPhone] the user wants to switch to, gated by the
     * previously-obtained [reauthToken]. Mirrors iOS `requestPhoneChangeOtp`. The SMS fires here.
     * The optional BCP-47 [language] tag (e.g. "en-US") localises the code message.
     *
     * @return [Result.success] on success, or [Result.failure] with a [ResolverError] (notably
     * [ResolverError.InvalidReauthToken], [ResolverError.PhoneAlreadyLinked], [ResolverError.RateLimited]).
     */
    suspend fun requestPhoneChangeOtp(accessToken: String, userId: String, newPhone: String, reauthToken: String, language: String?): Result<Unit>

    /**
     * Complete the phone-number change: verifies the OTP [code] sent to the new number, consumes the
     * [reauthToken], then re-points the account to [newPhone]. Mirrors iOS `changePhoneNumber`.
     *
     * @return [Result.success] on success, or [Result.failure] with a [ResolverError] (e.g.
     * [ResolverError.InvalidOtp], [ResolverError.InvalidReauthToken], [ResolverError.PhoneAlreadyLinked]).
     */
    suspend fun changePhoneNumber(accessToken: String, userId: String, newPhone: String, code: String, reauthToken: String): Result<Unit>

    // GUA FORK: Passkey enrollment. Android counterpart of iOS
    // `IdentityServiceClientProtocol.startPasskeyEnrollment`.

    /**
     * Start passkey enrollment and obtain the authenticated web-ceremony URL to open at the IdP.
     * Mirrors iOS `startPasskeyEnrollment(accessToken:)` (`POST /security/passkey/enroll/start`,
     * returning `{ "enrollUrl": ... }`).
     *
     * The returned URL is self-authenticating (it carries a short-lived enrollment token), so the
     * client just opens it in an authenticated web ceremony — on Android a Chrome Custom Tab — and
     * the user completes the WebAuthn registration in-browser at the IdP.
     *
     * @return [Result.success] with the enrollment URL, or [Result.failure] with a [ResolverError].
     */
    suspend fun startPasskeyEnrollment(accessToken: String): Result<String>
}
