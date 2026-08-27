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
import io.element.android.libraries.guaresolver.ContactMatch
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.PinStatus
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

    override suspend fun pinStatus(accessToken: String, userId: String): Result<PinStatus> =
        runPinCall { api ->
            val response = api.pinStatus(authorization = "Bearer $accessToken")
            PinStatus(
                hasPin = response.hasPin,
                changePhoneCooldownRemainingSeconds = response.changePhoneCooldownRemainingSeconds.coerceAtLeast(0),
            )
        }

    override suspend fun setInitialPin(accessToken: String, userId: String, newPin: String): Result<Unit> =
        runPinCall { api ->
            api.setInitialPin(
                authorization = "Bearer $accessToken",
                body = SetInitialPinRequest(userId = userId, newPin = newPin),
            )
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

    // GUA FORK: Change phone number (PIN-first). Mirrors iOS `IdentityServiceClient` phone-change
    // methods. The PIN step-up runs FIRST and yields a reauth token; the SMS only fires from
    // [requestPhoneChangeOtp].

    override suspend fun verifyPinReauth(accessToken: String, userId: String, pin: String): Result<String> =
        runPinCall { api ->
            api.verifyPinReauth(
                authorization = "Bearer $accessToken",
                body = PinReauthRequest(userId = userId, pin = pin),
            ).reauthToken
        }

    override suspend fun requestPhoneChangeOtp(
        accessToken: String,
        userId: String,
        newPhone: String,
        reauthToken: String,
        language: String?,
    ): Result<Unit> =
        runPinCall { api ->
            api.requestChangeNumberOtp(
                authorization = "Bearer $accessToken",
                body = OtpChangeNumberStartRequest(
                    userId = userId,
                    newPhone = newPhone,
                    reauthToken = reauthToken,
                    language = language,
                ),
            )
        }

    override suspend fun changePhoneNumber(
        accessToken: String,
        userId: String,
        newPhone: String,
        code: String,
        reauthToken: String,
    ): Result<Unit> =
        runPinCall { api ->
            api.changeNumber(
                authorization = "Bearer $accessToken",
                body = OtpChangeNumberRequest(userId = userId, newPhone = newPhone, code = code, reauthToken = reauthToken),
            )
        }

    // GUA FORK: Passkey enrollment. Mirrors iOS `IdentityServiceClient.startPasskeyEnrollment`.

    override suspend fun startPasskeyEnrollment(accessToken: String): Result<String> =
        runPinCall { api ->
            api.startPasskeyEnrollment(authorization = "Bearer $accessToken").enrollUrl
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
            "phone_already_linked" -> ResolverError.PhoneAlreadyLinked
            "invalid_reauth_token" -> ResolverError.InvalidReauthToken
            "pin_setup_required" -> ResolverError.PinSetupRequired
            "twofa_cooldown_active" -> ResolverError.TwoFactorCooldown(retryAfterSeconds = cooldownRetryAfter)
            "rate_limited" -> ResolverError.RateLimited
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
    }
}
