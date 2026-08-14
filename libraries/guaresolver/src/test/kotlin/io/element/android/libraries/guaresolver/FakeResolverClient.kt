/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: lambda-overridable fake [ResolverClient] for downstream presenter tests.
 */
class FakeResolverClient(
    private val resolveResult: (String) -> Result<HomeserverResolution> = {
        Result.success(aHomeserverResolution())
    },
) : ResolverClient {
    override suspend fun resolve(e164Phone: String): Result<HomeserverResolution> = resolveResult(e164Phone)
}

/**
 * GUA FORK: lambda-overridable fake [FederationRosterFetcher] that also counts fetches, for
 * roster cache tests.
 */
class FakeFederationRosterFetcher(
    var fetchRosterResult: () -> Result<FederationRoster> = { Result.success(aFederationRoster()) },
) : FederationRosterFetcher {
    var fetchCount: Int = 0
        private set

    override suspend fun fetchRoster(): Result<FederationRoster> {
        fetchCount++
        return fetchRosterResult()
    }
}

/**
 * GUA FORK: test-only [GuaDeployment] with explicit values, so tests can exercise the
 * configured / unconfigured resolver / identity-service paths without the build-time
 * [GuaResolverConfig] selection.
 */
data class FakeGuaDeployment(
    override val resolverBaseUrl: String? = "https://resolver.gua.global",
    override val defaultAccountProvider: String? = "gua.global",
    override val identityServiceBaseUrl: String? = "https://identity.gua.global",
) : GuaDeployment

/**
 * GUA FORK: lambda-overridable fake [IdentityServiceClient] for downstream presenter tests.
 */
class FakeIdentityServiceClient(
    private val lookupResult: (String, List<String>) -> Result<List<ContactMatch>> = { _, _ ->
        Result.success(emptyList())
    },
    private val pinStatusResult: (String) -> Result<Boolean> = { Result.success(false) },
    private val setInitialPinResult: (String, String, String) -> Result<Unit> = { _, _, _ -> Result.success(Unit) },
    private val startPinChangeResult: (String, String, String) -> Result<String> = { _, _, _ -> Result.success("challenge-id") },
    private val completePinChangeResult: (String, String, String, String) -> Result<Unit> = { _, _, _, _ -> Result.success(Unit) },
    private val verifyPinReauthResult: (String, String, String) -> Result<String> = { _, _, _ -> Result.success("reauth-token") },
    private val requestPhoneChangeOtpResult: (String, String, String, String, String?) -> Result<Unit> = { _, _, _, _, _ -> Result.success(Unit) },
    private val changePhoneNumberResult: (String, String, String, String, String) -> Result<Unit> = { _, _, _, _, _ -> Result.success(Unit) },
) : IdentityServiceClient {
    override suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>> =
        lookupResult(accessToken, hashedPhones)

    override suspend fun pinStatus(accessToken: String): Result<Boolean> =
        pinStatusResult(accessToken)

    override suspend fun setInitialPin(accessToken: String, userId: String, newPin: String): Result<Unit> =
        setInitialPinResult(accessToken, userId, newPin)

    override suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String> =
        startPinChangeResult(accessToken, phone, currentPin)

    override suspend fun completePinChange(accessToken: String, challengeId: String, otpCode: String, newPin: String): Result<Unit> =
        completePinChangeResult(accessToken, challengeId, otpCode, newPin)

    override suspend fun verifyPinReauth(accessToken: String, userId: String, pin: String): Result<String> =
        verifyPinReauthResult(accessToken, userId, pin)

    override suspend fun requestPhoneChangeOtp(
        accessToken: String,
        userId: String,
        newPhone: String,
        reauthToken: String,
        language: String?,
    ): Result<Unit> =
        requestPhoneChangeOtpResult(accessToken, userId, newPhone, reauthToken, language)

    override suspend fun changePhoneNumber(accessToken: String, userId: String, newPhone: String, code: String, reauthToken: String): Result<Unit> =
        changePhoneNumberResult(accessToken, userId, newPhone, code, reauthToken)
}

fun aContactMatch(
    hashedPhone: String = "deadbeef",
    userId: String = "@alice:gua.global",
    displayHandle: String = "@alice",
    displayName: String? = "Alice",
    avatarUrl: String? = null,
) = ContactMatch(
    hashedPhone = hashedPhone,
    userId = userId,
    displayHandle = displayHandle,
    displayName = displayName,
    avatarUrl = avatarUrl,
)

fun aResolvedHomeserver(
    serverName: String = "gua.global",
    baseUrl: String = "https://matrix.gua.global",
    masIssuer: String? = "https://mas.gua.global",
    region: String? = "br",
) = ResolvedHomeserver(
    serverName = serverName,
    baseUrl = baseUrl,
    masIssuer = masIssuer,
    region = region,
)

fun aHomeserverResolution(
    exists: Boolean = true,
    homeserver: ResolvedHomeserver = aResolvedHomeserver(),
) = HomeserverResolution(
    exists = exists,
    homeserver = homeserver,
)

fun aFederationRosterEntry(
    serverName: String = "gua.global",
    status: String = "ACTIVE",
    searchVisibility: String? = null,
    searchGroups: List<String>? = null,
) = FederationRosterEntry(
    homeserver = FederationRosterServer(
        serverName = serverName,
        searchVisibility = searchVisibility,
        searchGroups = searchGroups,
    ),
    status = status,
)

fun aFederationRoster(
    entries: List<FederationRosterEntry> = listOf(
        aFederationRosterEntry(serverName = "gua.global"),
        aFederationRosterEntry(serverName = "gua.ca"),
    ),
) = FederationRoster(
    entries = entries,
)
