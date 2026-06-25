/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * GUA FORK: Retrofit surface for the Gua identity-service `POST /directory/lookup` contact-discovery
 * endpoint. Internal to the module; the public API only ever exposes
 * [io.element.android.libraries.guaresolver.ContactMatch].
 *
 * The request carries **hashed** phone digests only (see
 * [io.element.android.libraries.guaresolver.PhoneHasher]) and is authenticated with the caller's
 * Matrix access token, mirroring iOS' `lookupContacts(accessToken:phones:)`.
 */
internal interface IdentityServiceApi {
    @POST("directory/lookup")
    suspend fun lookupContacts(
        @Header("Authorization") authorization: String,
        @Body body: LookupRequest,
    ): LookupResponse

    // GUA FORK: Two-step verification (account PIN). Mirrors iOS' `security/pin*` endpoints.

    @GET("security/pin/status")
    suspend fun pinStatus(
        @Header("Authorization") authorization: String,
    ): PinStatusResponse

    @POST("security/pin")
    suspend fun setInitialPin(
        @Header("Authorization") authorization: String,
        @Body body: SetInitialPinRequest,
    )

    @POST("security/pin/change/start")
    suspend fun startPinChange(
        @Header("Authorization") authorization: String,
        @Body body: StartPinChangeRequest,
    ): StartPinChangeResponse

    @POST("security/pin/change/complete")
    suspend fun completePinChange(
        @Header("Authorization") authorization: String,
        @Body body: CompletePinChangeRequest,
    )
}

@Serializable
internal data class LookupRequest(
    /** Hashed phone digests — never raw numbers. */
    val hashedPhones: List<String>,
)

@Serializable
internal data class LookupMatch(
    /** Echoes back the submitted hashed digest so the client can map the hit onto the address book. */
    val hashedPhone: String,
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
internal data class LookupResponse(
    val matches: List<LookupMatch> = emptyList(),
)

@Serializable
internal data class PinStatusResponse(
    val hasPin: Boolean,
)

@Serializable
internal data class SetInitialPinRequest(
    val userId: String,
    val newPin: String,
)

@Serializable
internal data class StartPinChangeRequest(
    val phone: String,
    val currentPin: String,
)

@Serializable
internal data class StartPinChangeResponse(
    val challengeId: String,
    val expiresInSeconds: Int? = null,
)

@Serializable
internal data class CompletePinChangeRequest(
    val challengeId: String,
    val otpCode: String,
    val newPin: String,
)

/**
 * Identity-service error envelope. The typed [io.element.android.libraries.guaresolver.ResolverError]
 * PIN cases are derived from the `code` field, mirroring iOS' `ErrorBody.code` handling.
 */
@Serializable
internal data class IdentityServiceErrorBody(
    val code: String? = null,
    val message: String? = null,
    val error: String? = null,
)
