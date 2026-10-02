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

    /**
     * This device holds no access token for the signed-in session, so an authenticated call could
     * not even be attempted. Surfaced by [withFreshAccessToken].
     */
    data object NoSession : ResolverError("This device is not signed in.")

    /**
     * The identity service refused the session's own credential, and it went on refusing after the
     * SDK had been given the chance to refresh it (HTTP 401 with no error code of its own).
     * Surfaced by [withFreshAccessToken].
     *
     * Its own case rather than a bare [Server] 401 because it is the one failure the user can clear
     * themselves by repeating the action, which the generic "something went wrong" never told them.
     */
    data object SessionRefreshNeeded : ResolverError("Your session needed refreshing. Please try again.")

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
     * The reauth token is missing, invalid, expired or already spent
     * (identity-service `code: "invalid_reauth_token"`, HTTP 401). The token is single-use and the
     * server spends it before it weighs the step-up factor, so the caller must mint a fresh one by
     * restarting the reauthentication rather than retrying the same token.
     */
    data object InvalidReauthToken : ResolverError("Your confirmation expired. Please start again.")

    /**
     * The phone-change challenge expired, was already spent, or belongs to someone else
     * (identity-service `code: "phone_change_challenge_invalid"`, HTTP 401).
     */
    data object PhoneChangeChallengeInvalid : ResolverError("Your phone change session expired. Please start over.")

    /**
     * The number submitted to reauthenticate is not the one bound to the signed-in account
     * (identity-service `code: "reauth_phone_mismatch"`, HTTP 403).
     *
     * Deliberately one case for three situations: the number belongs to nobody, it belongs to
     * someone else, or it is simply not this account's. The server answers all three identically so
     * a stolen session cannot use the reauth step to find out who owns a number, and the client must
     * keep it that way: never say anything about another account.
     */
    data object ReauthPhoneMismatch : ResolverError("That is not the number on your account.")

    /**
     * The number could not be read as a phone number at all (identity-service
     * `code: "invalid_phone_number"`, HTTP 400). It says nothing about who holds it, which is why it
     * is safe to distinguish from [ReauthPhoneMismatch].
     */
    data object InvalidPhoneNumber : ResolverError("That does not look like a phone number.")

    /**
     * Enrollment of a first PIN was started for an account that already has one
     * (identity-service `code: "pin_already_set"`, HTTP 409). Not a failure of the user's: the
     * client's view of the account's factors was simply stale, and changing the PIN is the operation
     * they actually want.
     */
    data object PinAlreadySet : ResolverError("That account already has a PIN.")

    /**
     * Enrollment of a passkey was started for an account that already holds one
     * (identity-service `code: "passkey_already_registered"`, HTTP 409). The twin of
     * [PinAlreadySet], and like it not the user's mistake: the screen's view of the account's
     * factors is stale, which is the ordinary outcome of registering the passkey in a Custom Tab
     * and coming back to a screen that has not read the account since.
     */
    data object PasskeyAlreadyRegistered : ResolverError("That account already has a passkey.")

    /**
     * The account's only factor is a passkey and this deployment cannot run a passkey ceremony, so
     * there is no proof it can produce for a step-up at all (identity-service
     * `code: "step_up_unavailable"`, HTTP 409).
     *
     * A dead end rather than something to retry: no factor can be added here until passkeys work
     * again, and the way back to a usable account is the delayed account recovery.
     */
    data object StepUpUnavailable : ResolverError("This account cannot confirm it is you right now.")

    /**
     * The redirect this build named on a factor-enrollment start is not one the deployment permits
     * (identity-service `code: "invalid_redirect_uri"`, HTTP 400).
     *
     * Not a case a screen is meant to render: the client answers it by asking again without naming
     * a redirect, which is what an older server and a deployment that has not allowlisted this
     * variant both accept, so the ceremony still opens and returns to the configured default. It
     * only ever reaches a caller if that second attempt is refused as well.
     */
    data object InvalidRedirectUri : ResolverError("This app cannot be returned to after enrolling.")

    /**
     * The operation demands a step-up factor and the account has NEITHER a PIN nor a passkey
     * registered (identity-service `code: "step_up_required"`, HTTP 403).
     *
     * This is a hard block, not a hint: the reauth token alone only proves an OTP sent to the number
     * being re-pointed, so there is no token-only fallback and the operation terminates here. The
     * caller routes the user into setting up a factor, passkey or PIN, and starts over afterwards.
     */
    data object StepUpRequired : ResolverError("Set up two-step verification before changing your number.")

    /**
     * The change-phone flow requires an account PIN to be set up first
     * (identity-service `code: "pin_setup_required"`, HTTP 400). Kept for identity-service builds
     * that predate [StepUpRequired]; newer ones answer with that instead. Treated the same way: the
     * account can settle no step-up, so the operation stops.
     */
    data object PinSetupRequired : ResolverError("You need to set up a PIN before changing your number.")

    /**
     * Two successful phone changes were attempted too close together
     * (identity-service `code: "phone_change_cooldown"`, HTTP 425). Distinct from
     * [TwoFactorCooldown], which is the hold on a freshly minted factor: waiting out one does not
     * clear the other.
     */
    data class PhoneChangeCooldown(val retryAfterSeconds: Long? = null) :
        ResolverError("For your security, you can change your number again later.")

    /**
     * The fresh-2FA cooldown is still active, so the phone number cannot be changed yet
     * (identity-service `code: "twofa_cooldown_active"`, HTTP 400). [retryAfterSeconds] is how long
     * the caller must wait before the change is allowed.
     */
    data class TwoFactorCooldown(val retryAfterSeconds: Long? = null) :
        ResolverError("For your security, you can change your number again later.")
}
