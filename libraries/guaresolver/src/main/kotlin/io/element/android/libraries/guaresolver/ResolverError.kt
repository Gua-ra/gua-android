/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: errors surfaced by [ResolverClient]. Mirrors iOS `ResolverError`.
 */
sealed class ResolverError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The resolver base URL is not configured for this build (dev secrets absent). */
    data object NotConfigured : ResolverError("The routing service is not configured.")

    /** The resolver returned an HTTP response that could not be understood. */
    data object MalformedResponse : ResolverError("The routing service returned an unexpected response.")

    /** The resolver returned a non-success HTTP status. */
    data class Server(val status: Int) : ResolverError("Routing service error ($status).")

    /** Transport or decoding failure while talking to the resolver. */
    data class Transport(val error: Throwable) : ResolverError("Could not reach the routing service.", error)

    // GUA FORK: two-step verification (account PIN). Mirrors the typed iOS `IdentityServiceError`
    // cases so presenters can drive the PIN state machine per error. Surfaced by [IdentityServiceClient]
    // PIN methods from the identity-service's JSON `code` field (see `DefaultIdentityServiceClient`).

    /** The supplied PIN is incorrect (identity-service `code: "invalid_pin"`). */
    data object InvalidPin : ResolverError("That PIN is incorrect. Please try again.")

    /** The supplied OTP code is invalid or expired (identity-service `code: "invalid_otp"`). */
    data object InvalidOtp : ResolverError("The code you entered is invalid or has expired.")

    /** The PIN is locked after too many wrong attempts (identity-service `code: "pin_locked"`). */
    data class PinLocked(val retryAfterSeconds: Int? = null) :
        ResolverError("PIN locked due to too many wrong attempts. Please try again later.")

    /** A PIN change was attempted within the cooldown window (identity-service `code: "pin_change_cooldown"`). */
    data class PinChangeCooldown(val retryAfterSeconds: Int? = null) :
        ResolverError("For security, you can only change your PIN once per day.")

    /** The PIN change challenge expired or is unknown (identity-service `code: "pin_change_challenge_invalid"`). */
    data object PinChangeChallengeInvalid : ResolverError("Your PIN change session expired. Please start over.")

    /** Too many attempts; the caller should back off (identity-service `code: "rate_limited"`). */
    data object RateLimited : ResolverError("Too many attempts. Please wait a moment and try again.")

    /**
     * The requested phone number is already linked to another account
     * (identity-service `code: "phone_already_linked"`, HTTP 409).
     */
    data object PhoneAlreadyLinked : ResolverError("That phone number is already linked to another account.")

    /**
     * The PIN step-up reauth token is missing, invalid, or expired
     * (identity-service `code: "invalid_reauth_token"`, HTTP 401). The caller should restart the
     * flow from the PIN step.
     */
    data object InvalidReauthToken : ResolverError("Your confirmation expired. Please re-enter your PIN.")

    /**
     * The change-phone flow requires an account PIN to be set up first
     * (identity-service `code: "pin_setup_required"`, HTTP 400). Distinct from [InvalidPin]: the user
     * has no PIN at all. The caller should route into the 2SV PIN-setup flow.
     */
    data object PinSetupRequired : ResolverError("You need to set up a PIN before changing your number.")

    /**
     * The fresh-2FA cooldown is still active, so the phone number cannot be changed yet
     * (identity-service `code: "twofa_cooldown_active"`, HTTP 400). [retryAfterSeconds] is how long
     * the caller must wait before the change is allowed.
     */
    data class TwoFactorCooldown(val retryAfterSeconds: Long? = null) :
        ResolverError("For your security, you can change your number again later.")
}
