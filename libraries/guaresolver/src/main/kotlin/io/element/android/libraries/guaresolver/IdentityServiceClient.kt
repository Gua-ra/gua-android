/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * Callers pass hashed phone digests, never raw numbers. See [PhoneHasher].
 * A bearer session alone must never add a durable factor: every factor enrollment runs as an authenticated web ceremony.
 */
interface IdentityServiceClient {
    suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>>

    /** A failure means the factors are unknown, not "no factors". */
    suspend fun accountFactorStatus(accessToken: String, userId: String): Result<AccountFactorStatus>

    /** Succeeds whether or not a recovery was live, so a retry is safe. */
    suspend fun cancelAccountRecovery(accessToken: String): Result<Unit>

    /** Returns the web-ceremony URL for enrolling the first PIN. Fails with [ResolverError.PinAlreadySet] when the account already holds one. */
    suspend fun startPinEnrollment(accessToken: String): Result<String>

    /** Verifies the current PIN and triggers an OTP to [phone]. Returns the challenge id. */
    suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String>

    suspend fun completePinChange(accessToken: String, challengeId: String, otpCode: String, newPin: String): Result<Unit>

    // Nothing is sent to the new number before [startPhoneChange] has spent the reauth token and accepted a step-up factor.

    /** Sends a reauth OTP only when [phone] is the number bound to the account. */
    suspend fun startPhoneChangeReauth(accessToken: String, phone: String, language: String?): Result<Unit>

    /** Exchanges the reauth OTP for a single-use token. [phone] is sent again because the server keeps nothing between the two calls. */
    suspend fun verifyPhoneChangeReauth(accessToken: String, phone: String, code: String): Result<String>

    /** Spends [reauthToken] and a step-up factor, then sends an OTP to [newPhone]. The token is gone on every outcome. */
    suspend fun startPhoneChange(
        accessToken: String,
        reauthToken: String,
        newPhone: String,
        pin: String?,
        passkeyStepUpId: String?,
        passkeyCredentialJson: String?,
        language: String?,
    ): Result<PhoneChangeChallenge>

    suspend fun completePhoneChange(accessToken: String, challengeId: String, code: String): Result<Unit>

    /** Returns the web-ceremony URL for passkey enrollment. */
    suspend fun startPasskeyEnrollment(accessToken: String): Result<String>

    /**
     * Unauthenticated: [proofB64Url] is a signature under the authority key committed inside [genesisB64Url]. A 503 means the deployment does not do account
     * genesis.
     */
    suspend fun registerAccountGenesis(genesisB64Url: String, proofB64Url: String): Result<AccountGenesisRegistration>
}

data class PhoneChangeChallenge(
    val challengeId: String,
    /** Seconds before the new-number OTP expires, or null when the server did not say. */
    val otpExpiresInSeconds: Long?,
)

/** The handle is a routing hint, not a capability. */
data class AccountGenesisRegistration(
    val accountId: String,
    val attachHandle: String,
)
