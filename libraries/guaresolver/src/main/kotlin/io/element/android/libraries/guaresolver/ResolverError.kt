/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

sealed class ResolverError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object NotConfigured : ResolverError("The routing service is not configured.")

    data object MalformedResponse : ResolverError("The routing service returned an unexpected response.")

    data class Server(val status: Int) : ResolverError("Routing service error ($status).")

    data class Transport(val error: Throwable) : ResolverError("Could not reach the routing service.", error)

    data object InvalidPin : ResolverError("That PIN is incorrect. Please try again.")

    data object InvalidOtp : ResolverError("The code you entered is invalid or has expired.")

    data class PinLocked(val retryAfterSeconds: Int? = null) :
        ResolverError("PIN locked due to too many wrong attempts. Please try again later.")

    data class PinChangeCooldown(val retryAfterSeconds: Int? = null) :
        ResolverError("For security, you can only change your PIN once per day.")

    data object PinChangeChallengeInvalid : ResolverError("Your PIN change session expired. Please start over.")

    data object RateLimited : ResolverError("Too many attempts. Please wait a moment and try again.")

    data object PhoneAlreadyLinked : ResolverError("That phone number is already linked to another account.")

    /** Single-use: the caller must mint a fresh token, never retry the same one. */
    data object InvalidReauthToken : ResolverError("Your confirmation expired. Please start again.")

    data object PhoneChangeChallengeInvalid : ResolverError("Your phone change session expired. Please start over.")

    /** One case for "unknown", "someone else's" and "not this account's". The client must never say which. */
    data object ReauthPhoneMismatch : ResolverError("That is not the number on your account.")

    data object InvalidPhoneNumber : ResolverError("That does not look like a phone number.")

    /** The client's view of the account's factors was stale. */
    data object PinAlreadySet : ResolverError("That account already has a PIN.")

    /** The client's view of the account's factors was stale. */
    data object PasskeyAlreadyRegistered : ResolverError("That account already has a passkey.")

    /** The account's only factor is a passkey and this deployment cannot run a passkey ceremony. */
    data object StepUpUnavailable : ResolverError("This account cannot confirm it is you right now.")

    /** Answered by retrying once without a redirect. */
    data object InvalidRedirectUri : ResolverError("This app cannot be returned to after enrolling.")

    /** A hard block: there is no token-only fallback. */
    data object StepUpRequired : ResolverError("Set up two-step verification before changing your number.")

    /** Sent by identity-service builds that predate [StepUpRequired]. Treated the same way. */
    data object PinSetupRequired : ResolverError("You need to set up a PIN before changing your number.")

    /** Distinct from [TwoFactorCooldown]: waiting out one does not clear the other. */
    data class PhoneChangeCooldown(val retryAfterSeconds: Long? = null) :
        ResolverError("For your security, you can change your number again later.")

    data class TwoFactorCooldown(val retryAfterSeconds: Long? = null) :
        ResolverError("For your security, you can change your number again later.")
}
