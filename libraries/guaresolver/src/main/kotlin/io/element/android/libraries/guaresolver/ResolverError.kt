/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * Errors surfaced by [ResolverClient] and [IdentityServiceClient]. The identity-service cases are
 * derived from the JSON `code` field of the error body, so presenters can branch per error.
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
     * server spends it before it weighs the step-up factor, so the caller must mint a fresh one rather
     * than retry the same token.
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
     * One case for three situations: the number belongs to nobody, to someone else, or is simply not
     * this account's. The server answers all three identically so a stolen session cannot learn who owns
     * a number. The client must never say anything about another account.
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
     * (identity-service `code: "pin_already_set"`, HTTP 409). The client's view of the account's factors
     * was stale, and changing the PIN is the operation the user wants.
     */
    data object PinAlreadySet : ResolverError("That account already has a PIN.")

    /**
     * Enrollment of a passkey was started for an account that already holds one
     * (identity-service `code: "passkey_already_registered"`, HTTP 409). Like [PinAlreadySet], the
     * screen's view of the account's factors was stale.
     */
    data object PasskeyAlreadyRegistered : ResolverError("That account already has a passkey.")

    /**
     * The account's only factor is a passkey and this deployment cannot run a passkey ceremony
     * (identity-service `code: "step_up_unavailable"`, HTTP 409). A dead end rather than something to
     * retry: the way back to a usable account is the delayed account recovery.
     */
    data object StepUpUnavailable : ResolverError("This account cannot confirm it is you right now.")

    /**
     * The redirect this build named on a factor-enrollment start is not one the deployment permits
     * (identity-service `code: "invalid_redirect_uri"`, HTTP 400). The client answers it by asking once
     * more without a redirect, so it only reaches a caller if that retry is refused as well.
     */
    data object InvalidRedirectUri : ResolverError("This app cannot be returned to after enrolling.")

    /**
     * The operation demands a step-up factor and the account has neither a PIN nor a passkey
     * (identity-service `code: "step_up_required"`, HTTP 403). A hard block: there is no token-only
     * fallback. The caller routes the user into setting up a factor and starts over afterwards.
     */
    data object StepUpRequired : ResolverError("Set up two-step verification before changing your number.")

    /**
     * The change-phone flow requires an account PIN first (identity-service
     * `code: "pin_setup_required"`, HTTP 400). Sent by identity-service builds that predate
     * [StepUpRequired] and treated the same way.
     */
    data object PinSetupRequired : ResolverError("You need to set up a PIN before changing your number.")

    /**
     * Two successful phone changes were attempted too close together
     * (identity-service `code: "phone_change_cooldown"`, HTTP 425). Distinct from [TwoFactorCooldown]:
     * waiting out one does not clear the other.
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
