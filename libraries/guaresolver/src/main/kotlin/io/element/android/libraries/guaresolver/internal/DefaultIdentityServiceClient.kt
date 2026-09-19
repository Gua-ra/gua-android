/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.AccountGenesisRegistration
import io.element.android.libraries.guaresolver.AuthFactor
import io.element.android.libraries.guaresolver.ContactMatch
import io.element.android.libraries.guaresolver.EnrollmentRedirectProvider
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.PhoneChangeChallenge
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber

/**
 * GUA FORK: default [IdentityServiceClient]. Talks to the active [GuaDeployment]'s identity-service
 * via Retrofit, reusing the app-wide [RetrofitFactory] (OkHttp + kotlinx-serialization). Mirrors iOS
 * `IdentityServiceClient.lookupContacts`.
 *
 * PRIVACY: only hashed phone digests are sent (the caller is expected to protect raw E.164 numbers
 * via `PhoneHasher` first), the address book is never persisted, and nothing about the contacts is
 * logged. The lookup is authenticated with the caller's Matrix access token over TLS.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultIdentityServiceClient(
    private val retrofitFactory: RetrofitFactory,
    private val enrollmentRedirectProvider: EnrollmentRedirectProvider,
    private val deployment: GuaDeployment = GuaResolverConfig.current,
) : IdentityServiceClient {
    override suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>> {
        if (hashedPhones.isEmpty()) return Result.success(emptyList())

        val baseUrl = deployment.identityServiceBaseUrl
            ?: return Result.failure(ResolverError.NotConfigured)

        val api = try {
            retrofitFactory.create(baseUrl.ensureProtocol()).create(IdentityServiceApi::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create identity-service Retrofit instance")
            return Result.failure(ResolverError.Transport(e))
        }

        val response = try {
            api.lookupContacts(
                authorization = "Bearer $accessToken",
                body = LookupRequest(hashedPhones = hashedPhones),
            )
        } catch (e: HttpException) {
            return Result.failure(ResolverError.Server(e.code()))
        } catch (e: Exception) {
            Timber.e(e, "Contact lookup failed")
            return Result.failure(ResolverError.Transport(e))
        }

        return Result.success(
            response.matches.map { match ->
                ContactMatch(
                    hashedPhone = match.hashedPhone,
                    userId = match.userId,
                    // Homeserver abstraction: prefer the assigned global username, otherwise strip
                    // the ":homeserver" suffix from the Matrix id (mirrors iOS `DiscoveredContact.handle`
                    // and the Android `UserId.displayHandle`). Never surface the homeserver to users.
                    displayHandle = displayHandle(username = match.username, userId = match.userId),
                    displayName = match.displayName,
                    avatarUrl = match.avatarUrl,
                )
            }
        )
    }

    private fun displayHandle(username: String?, userId: String): String {
        if (!username.isNullOrEmpty()) {
            return if (username.startsWith("@")) username else "@$username"
        }
        return if (userId.startsWith("@")) {
            "@" + userId.removePrefix("@").substringBefore(":")
        } else {
            userId
        }
    }

    // GUA FORK: Two-step verification (account PIN). Mirrors iOS `IdentityServiceClient` PIN methods.

    override suspend fun accountFactorStatus(accessToken: String, userId: String): Result<AccountFactorStatus> =
        runPinCall { api ->
            val response = api.pinStatus(authorization = "Bearer $accessToken")
            val hasPin = response.hasPin
            val passkeyRegistered = response.passkeyRegistered
            AccountFactorStatus(
                hasPin = hasPin,
                passkeyRegistered = passkeyRegistered,
                // An identity-service that predates the factor policy sends neither field. Derive
                // them from what the account is known to hold rather than defaulting to "nothing",
                // which would hard-block a phone change the old server would have allowed.
                preferredFactor = AuthFactor.fromWire(response.preferredFactor)
                    ?: strongestHeld(passkeyRegistered = passkeyRegistered, hasPin = hasPin),
                phoneChangeStepUpFactors = response.phoneChangeStepUpFactors
                    .mapNotNull(AuthFactor::fromWire)
                    .ifEmpty { DEFAULT_PHONE_CHANGE_STEP_UP_FACTORS },
                changePhoneCooldownRemainingSeconds = response.changePhoneCooldownRemainingSeconds.coerceAtLeast(0),
                accountRecoveryPending = response.accountRecoveryPending,
                // Times only mean something while a recovery is live; never report a stale one.
                accountRecoveryCompletableAtEpochSeconds = response.accountRecoveryCompletableAtEpochSeconds
                    ?.takeIf { response.accountRecoveryPending },
                accountRecoveryExpiresAtEpochSeconds = response.accountRecoveryExpiresAtEpochSeconds
                    ?.takeIf { response.accountRecoveryPending },
            )
        }

    private fun strongestHeld(passkeyRegistered: Boolean, hasPin: Boolean): AuthFactor = when {
        passkeyRegistered -> AuthFactor.PASSKEY
        hasPin -> AuthFactor.PIN
        else -> AuthFactor.PHONE_OTP
    }

    override suspend fun cancelAccountRecovery(accessToken: String): Result<Unit> =
        runPinCall { api ->
            api.cancelAccountRecovery(authorization = "Bearer $accessToken")
        }

    override suspend fun startPinEnrollment(accessToken: String): Result<String> =
        startFactorEnrollment { api, body ->
            api.startPinEnrollment(authorization = "Bearer $accessToken", body = body).enrollUrl
        }

    override suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String> =
        runPinCall { api ->
            api.startPinChange(
                authorization = "Bearer $accessToken",
                body = StartPinChangeRequest(phone = phone, currentPin = currentPin),
            ).challengeId
        }

    override suspend fun completePinChange(
        accessToken: String,
        challengeId: String,
        otpCode: String,
        newPin: String,
    ): Result<Unit> =
        runPinCall { api ->
            api.completePinChange(
                authorization = "Bearer $accessToken",
                body = CompletePinChangeRequest(challengeId = challengeId, otpCode = otpCode, newPin = newPin),
            )
        }

    // GUA FORK: Change phone number, against the real `/account` contract: the current number, then
    // a reauth OTP to it, then a token, then a step-up factor plus the new number, then the
    // new-number OTP. The SMS to the new number is sent by `startPhoneChange`, which the server only
    // reaches once it has accepted a step-up factor, so nothing earlier in this sequence can text
    // the new number.

    override suspend fun startPhoneChangeReauth(accessToken: String, phone: String, language: String?): Result<Unit> =
        runPinCall { api ->
            api.startAccountReauth(
                authorization = "Bearer $accessToken",
                acceptLanguage = language,
                body = AccountReauthStartRequest(phone = phone),
            )
        }

    override suspend fun verifyPhoneChangeReauth(accessToken: String, phone: String, code: String): Result<String> =
        runPinCall { api ->
            api.verifyAccountReauth(
                authorization = "Bearer $accessToken",
                // Always scoped. The server binds the token to this operation and refuses to spend a
                // token minted for another one, so leaving the default (DEACTIVATE) in place would
                // hand back a token that the phone change cannot use.
                body = AccountReauthVerifyRequest(phone = phone, code = code, operation = PHONE_CHANGE_OPERATION),
            ).reauthToken
        }

    override suspend fun startPhoneChange(
        accessToken: String,
        reauthToken: String,
        newPhone: String,
        pin: String?,
        passkeyStepUpId: String?,
        passkeyCredentialJson: String?,
        language: String?,
    ): Result<PhoneChangeChallenge> =
        runPinCall { api ->
            val response = api.startPhoneChange(
                authorization = "Bearer $accessToken",
                acceptLanguage = language,
                body = PhoneChangeStartRequest(
                    reauthToken = reauthToken,
                    newPhone = newPhone,
                    pin = pin?.takeIf { it.isNotEmpty() },
                    passkeyStepUpId = passkeyStepUpId?.takeIf { it.isNotEmpty() },
                    passkeyCredential = passkeyCredentialJson?.let { credentialJson.parseToJsonElement(it) },
                ),
            )
            PhoneChangeChallenge(
                challengeId = response.challengeId,
                otpExpiresInSeconds = response.otpExpiresInSeconds,
            )
        }

    override suspend fun completePhoneChange(accessToken: String, challengeId: String, code: String): Result<Unit> =
        runPinCall { api ->
            api.completePhoneChange(
                authorization = "Bearer $accessToken",
                body = PhoneChangeCompleteRequest(challengeId = challengeId, code = code),
            )
        }

    // GUA FORK: Passkey enrollment. Mirrors iOS `IdentityServiceClient.startPasskeyEnrollment`.

    override suspend fun startPasskeyEnrollment(accessToken: String): Result<String> =
        startFactorEnrollment { api, body ->
            api.startPasskeyEnrollment(authorization = "Bearer $accessToken", body = body).enrollUrl
        }

    /**
     * Runs a factor-enrollment start, naming this build's own redirect so the ceremony comes back to
     * the app it was opened from rather than to whichever variant the deployment happens to have
     * configured.
     *
     * The named value is only ever a request. A server that predates the field ignores it, and one
     * that has not allowlisted this variant refuses the whole call with 400 `invalid_redirect_uri`,
     * so that refusal is answered once by asking again with no redirect at all. Enrollment then
     * proceeds exactly as it did before this existed, which is what keeps a QA build off a dead end
     * on a deployment nobody has updated yet. The retry runs at most once: a second refusal is
     * surfaced rather than looped on.
     */
    private inline fun startFactorEnrollment(
        call: (IdentityServiceApi, FactorEnrollStartRequest) -> String,
    ): Result<String> {
        val redirectUri = enrollmentRedirectProvider.provide()?.takeIf { it.isNotBlank() }
            ?: return runPinCall { api -> call(api, FactorEnrollStartRequest()) }

        val named = runPinCall { api -> call(api, FactorEnrollStartRequest(redirectUri = redirectUri)) }
        if (named.exceptionOrNull() !is ResolverError.InvalidRedirectUri) return named

        // Never log the value itself: it names the build, and the server deliberately does not echo
        // it back either.
        Timber.w("The identity service refused this build's enrollment redirect, starting again without one")
        return runPinCall { api -> call(api, FactorEnrollStartRequest()) }
    }

    // GUA FORK: account genesis registration (ADM-008 Phase 3). No access token: the request carries its
    // own possession proof, because it runs before any login session exists.

    override suspend fun registerAccountGenesis(genesisB64Url: String, proofB64Url: String): Result<AccountGenesisRegistration> =
        runPinCall { api ->
            val response = api.registerAccountGenesis(
                body = AccountGenesisRegisterRequest(genesis = genesisB64Url, proof = proofB64Url),
            )
            AccountGenesisRegistration(accountId = response.accountId, attachHandle = response.attachHandle)
        }

    /**
     * Runs an identity-service PIN call against a freshly-built [IdentityServiceApi], mapping HTTP
     * failures onto the typed [ResolverError] PIN cases (mirroring iOS' status-code + `code`-field
     * handling) and everything else onto [ResolverError.Transport].
     */
    private inline fun <T> runPinCall(block: (IdentityServiceApi) -> T): Result<T> {
        val baseUrl = deployment.identityServiceBaseUrl
            ?: return Result.failure(ResolverError.NotConfigured)

        val api = try {
            retrofitFactory.create(baseUrl.ensureProtocol()).create(IdentityServiceApi::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create identity-service Retrofit instance")
            return Result.failure(ResolverError.Transport(e))
        }

        return try {
            Result.success(block(api))
        } catch (e: HttpException) {
            Result.failure(e.toPinError())
        } catch (e: Exception) {
            Timber.e(e, "Identity-service PIN call failed")
            Result.failure(ResolverError.Transport(e))
        }
    }

    /**
     * Maps an [HttpException] from a PIN endpoint onto a typed [ResolverError], parsing the JSON
     * `code` field from the error body. Mirrors iOS `sendAuthenticated`'s status-code switch.
     */
    private fun HttpException.toPinError(): ResolverError {
        val rawBody = response()?.errorBody()?.string()
        val errorBody = rawBody?.let {
            tryOrNull { errorBodyJson.decodeFromString<IdentityServiceErrorBody>(it) }
        }
        val code = errorBody?.code
        val retryAfter = response()?.headers()?.get("Retry-After")?.toIntOrNull()
        // The cooldown retry window is carried in the JSON body, falling back to the Retry-After header.
        val cooldownRetryAfter = errorBody?.retryAfterSeconds ?: retryAfter?.toLong()
        return when (code) {
            "invalid_pin" -> ResolverError.InvalidPin
            "invalid_otp" -> ResolverError.InvalidOtp
            "pin_locked" -> ResolverError.PinLocked(retryAfterSeconds = retryAfter)
            "pin_change_cooldown" -> ResolverError.PinChangeCooldown(retryAfterSeconds = retryAfter)
            "pin_change_challenge_invalid" -> ResolverError.PinChangeChallengeInvalid
            "phone_change_challenge_invalid" -> ResolverError.PhoneChangeChallengeInvalid
            "phone_already_linked" -> ResolverError.PhoneAlreadyLinked
            "invalid_reauth_token" -> ResolverError.InvalidReauthToken
            // The submitted number is not the account's. One case for "unknown", "someone else's"
            // and "not this one", because the server answers all three the same way on purpose.
            "reauth_phone_mismatch" -> ResolverError.ReauthPhoneMismatch
            "invalid_phone_number" -> ResolverError.InvalidPhoneNumber
            "pin_already_set" -> ResolverError.PinAlreadySet
            // The enrollment refusals that are not the caller's fault. Left unnamed they became the
            // generic "Something went wrong", which is exactly wrong for the one account that
            // genuinely cannot enroll here and for the one that already finished enrolling.
            "passkey_already_registered" -> ResolverError.PasskeyAlreadyRegistered
            "step_up_unavailable" -> ResolverError.StepUpUnavailable
            // Named so `startFactorEnrollment` can tell this refusal apart from every other 400 and
            // start again without a redirect. Left as a bare server error it would have dead-ended
            // the one flow it exists to keep open.
            "invalid_redirect_uri" -> ResolverError.InvalidRedirectUri
            // The account holds neither a PIN nor a passkey. A hard block, mapped to its own case so
            // no caller can mistake it for one of the retryable PIN failures below.
            "step_up_required" -> ResolverError.StepUpRequired
            "pin_setup_required" -> ResolverError.PinSetupRequired
            "phone_change_cooldown" -> ResolverError.PhoneChangeCooldown(retryAfterSeconds = cooldownRetryAfter)
            "twofa_cooldown_active" -> ResolverError.TwoFactorCooldown(retryAfterSeconds = cooldownRetryAfter)
            "rate_limited" -> ResolverError.RateLimited
            // Status-only fallbacks, for a response that carried no `code` at all. Deliberately
            // narrow: a bare 403 is left as a plain server error, because 403 is not on its own a
            // step-up refusal and other endpoints answer with it for reasons of their own.
            else -> when (code()) {
                409 -> ResolverError.PhoneAlreadyLinked
                425 -> ResolverError.PinChangeCooldown(retryAfterSeconds = retryAfter)
                429 -> ResolverError.RateLimited
                else -> ResolverError.Server(code())
            }
        }
    }

    private companion object {
        private val errorBodyJson = Json { ignoreUnknownKeys = true }

        /** Re-serialises the authenticator's assertion response without reinterpreting it. */
        private val credentialJson = Json { ignoreUnknownKeys = true }

        /** The reauth scope a phone change demands; anything else the server refuses to spend here. */
        private const val PHONE_CHANGE_OPERATION = "PHONE_CHANGE"

        /**
         * What a phone change accepts when the identity-service is too old to say. Strongest first,
         * matching the server's own order; [AccountFactorStatus.phoneChangeStepUpOptions] then keeps
         * only the ones the account actually holds.
         */
        private val DEFAULT_PHONE_CHANGE_STEP_UP_FACTORS = listOf(AuthFactor.PASSKEY, AuthFactor.PIN)
    }
}
