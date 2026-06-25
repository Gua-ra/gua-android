/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.guaresolver.ContactMatch
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.IdentityServiceClient
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
@Inject
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

    override suspend fun pinStatus(accessToken: String): Result<Boolean> =
        runPinCall { api -> api.pinStatus(authorization = "Bearer $accessToken").hasPin }

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
        val code = rawBody?.let {
            runCatching { errorBodyJson.decodeFromString<IdentityServiceErrorBody>(it).code }.getOrNull()
        }
        val retryAfter = response()?.headers()?.get("Retry-After")?.toIntOrNull()
        return when (code) {
            "invalid_pin" -> ResolverError.InvalidPin
            "invalid_otp" -> ResolverError.InvalidOtp
            "pin_locked" -> ResolverError.PinLocked(retryAfterSeconds = retryAfter)
            "pin_change_cooldown" -> ResolverError.PinChangeCooldown(retryAfterSeconds = retryAfter)
            "pin_change_challenge_invalid" -> ResolverError.PinChangeChallengeInvalid
            "rate_limited" -> ResolverError.RateLimited
            else -> when (code()) {
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
