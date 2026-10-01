/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

class FakeResolverClient(
    private val resolveResult: (String) -> Result<HomeserverResolution> = {
        Result.success(aHomeserverResolution())
    },
) : ResolverClient {
    override suspend fun resolve(e164Phone: String): Result<HomeserverResolution> = resolveResult(e164Phone)
}

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

data class FakeGuaDeployment(
    override val resolverBaseUrl: String? = "https://resolver.gua.global",
    override val defaultAccountProvider: String? = "gua.global",
    override val identityServiceBaseUrl: String? = "https://identity.gua.global",
) : GuaDeployment

class FakeEnrollmentRedirectProvider(
    private val redirectUri: String? = "global.gua.dev:/oidc",
) : EnrollmentRedirectProvider {
    override fun provide(): String? = redirectUri
}

class FakeIdentityServiceClient(
    private val lookupResult: (String, List<String>) -> Result<List<ContactMatch>> = { _, _ ->
        Result.success(emptyList())
    },
    private val accountFactorStatusResult: (String, String) -> Result<AccountFactorStatus> = { _, _ ->
        Result.success(anAccountFactorStatus())
    },
    private val cancelAccountRecoveryResult: (String) -> Result<Unit> = { _ -> Result.success(Unit) },
    private val startPinEnrollmentResult: (String) -> Result<String> = { _ -> Result.success("https://idp.gua.global/pin/enroll?token=fake") },
    private val startPinChangeResult: (String, String, String) -> Result<String> = { _, _, _ -> Result.success("challenge-id") },
    private val completePinChangeResult: (String, String, String, String) -> Result<Unit> = { _, _, _, _ -> Result.success(Unit) },
    private val startPhoneChangeReauthResult: (String, String, String?) -> Result<Unit> = { _, _, _ -> Result.success(Unit) },
    private val verifyPhoneChangeReauthResult: (String, String, String) -> Result<String> = { _, _, _ -> Result.success(A_FAKE_REAUTH_TOKEN) },
    private val startPhoneChangeResult: (PhoneChangeStartCall) -> Result<PhoneChangeChallenge> = { _ ->
        Result.success(PhoneChangeChallenge(challengeId = A_FAKE_CHALLENGE_ID, otpExpiresInSeconds = 300))
    },
    private val completePhoneChangeResult: (String, String, String) -> Result<Unit> = { _, _, _ -> Result.success(Unit) },
    private val startPasskeyEnrollmentResult: (String) -> Result<String> = { _ -> Result.success("https://idp.gua.global/passkey/enroll?token=fake") },
    private val registerAccountGenesisResult: (String, String) -> Result<AccountGenesisRegistration> = { _, _ ->
        Result.success(AccountGenesisRegistration(accountId = A_FAKE_ACCOUNT_ID, attachHandle = A_FAKE_ATTACH_HANDLE))
    },
) : IdentityServiceClient {
    override suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>> =
        lookupResult(accessToken, hashedPhones)

    val startPhoneChangeCalls: MutableList<PhoneChangeStartCall> = mutableListOf()

    val startPhoneChangeReauthCalls: MutableList<Pair<String, String?>> = mutableListOf()

    override suspend fun accountFactorStatus(accessToken: String, userId: String): Result<AccountFactorStatus> =
        accountFactorStatusResult(accessToken, userId)

    override suspend fun cancelAccountRecovery(accessToken: String): Result<Unit> =
        cancelAccountRecoveryResult(accessToken)

    override suspend fun startPinEnrollment(accessToken: String): Result<String> =
        startPinEnrollmentResult(accessToken)

    override suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String> =
        startPinChangeResult(accessToken, phone, currentPin)

    override suspend fun completePinChange(accessToken: String, challengeId: String, otpCode: String, newPin: String): Result<Unit> =
        completePinChangeResult(accessToken, challengeId, otpCode, newPin)

    override suspend fun startPhoneChangeReauth(accessToken: String, phone: String, language: String?): Result<Unit> {
        startPhoneChangeReauthCalls += phone to language
        return startPhoneChangeReauthResult(accessToken, phone, language)
    }

    override suspend fun verifyPhoneChangeReauth(accessToken: String, phone: String, code: String): Result<String> =
        verifyPhoneChangeReauthResult(accessToken, phone, code)

    override suspend fun startPhoneChange(
        accessToken: String,
        reauthToken: String,
        newPhone: String,
        pin: String?,
        passkeyStepUpId: String?,
        passkeyCredentialJson: String?,
        language: String?,
    ): Result<PhoneChangeChallenge> {
        val call = PhoneChangeStartCall(
            reauthToken = reauthToken,
            newPhone = newPhone,
            pin = pin,
            passkeyStepUpId = passkeyStepUpId,
            passkeyCredentialJson = passkeyCredentialJson,
            language = language,
        )
        startPhoneChangeCalls += call
        return startPhoneChangeResult(call)
    }

    override suspend fun completePhoneChange(accessToken: String, challengeId: String, code: String): Result<Unit> =
        completePhoneChangeResult(accessToken, challengeId, code)

    override suspend fun startPasskeyEnrollment(accessToken: String): Result<String> =
        startPasskeyEnrollmentResult(accessToken)

    override suspend fun registerAccountGenesis(genesisB64Url: String, proofB64Url: String): Result<AccountGenesisRegistration> =
        registerAccountGenesisResult(genesisB64Url, proofB64Url)

    companion object {
        const val A_FAKE_ACCOUNT_ID = "ga1aea6aqb5opmzmutench3ggzepkhgwmkajb3epqqrhckkf7bcbcwl2cy"

        const val A_FAKE_ATTACH_HANDLE = "Zm9vYmFyYmF6cXV1eGNvcmdlZ3JhdWx0"

        const val A_FAKE_REAUTH_TOKEN = "reauth-token"

        const val A_FAKE_CHALLENGE_ID = "phone-change-challenge"
    }
}

data class PhoneChangeStartCall(
    val reauthToken: String,
    val newPhone: String,
    val pin: String?,
    val passkeyStepUpId: String?,
    val passkeyCredentialJson: String?,
    val language: String?,
)

fun anAccountFactorStatus(
    hasPin: Boolean = false,
    passkeyRegistered: Boolean = false,
    preferredFactor: AuthFactor = when {
        passkeyRegistered -> AuthFactor.PASSKEY
        hasPin -> AuthFactor.PIN
        else -> AuthFactor.PHONE_OTP
    },
    phoneChangeStepUpFactors: List<AuthFactor> = listOf(AuthFactor.PASSKEY, AuthFactor.PIN),
    changePhoneCooldownRemainingSeconds: Long = 0,
) = AccountFactorStatus(
    hasPin = hasPin,
    passkeyRegistered = passkeyRegistered,
    preferredFactor = preferredFactor,
    phoneChangeStepUpFactors = phoneChangeStepUpFactors,
    changePhoneCooldownRemainingSeconds = changePhoneCooldownRemainingSeconds,
)

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
