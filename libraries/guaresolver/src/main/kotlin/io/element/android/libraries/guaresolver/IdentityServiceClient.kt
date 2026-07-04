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
     * Whether the account already has a two-step-verification PIN set.
     *
     * @return [Result.success] with `true` when a PIN is set, or [Result.failure] with a [ResolverError].
     */
    suspend fun pinStatus(accessToken: String): Result<Boolean>

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
     *
     * @param language optional BCP-47 language tag (e.g. "en-US") so the code message is localised.
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
}
