/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.fixtures

import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.AccountGenesisRegistration
import io.element.android.libraries.guaresolver.AuthFactor
import io.element.android.libraries.guaresolver.ContactMatch
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.PhoneChangeChallenge

/**
 * GUA FORK: an [IdentityServiceClient] that records the calls it receives, so tests can assert both
 * what was sent and, just as importantly, what was NOT: no SMS may be requested for the new number
 * before a step-up factor has been offered.
 */
class FakeIdentityServiceClient(
    private val factorStatusResult: () -> Result<AccountFactorStatus> = { Result.success(aFactorStatus()) },
    private val startReauthResult: () -> Result<Unit> = { Result.success(Unit) },
    private val verifyReauthResult: () -> Result<String> = { Result.success(A_REAUTH_TOKEN) },
    private val startPhoneChangeResult: () -> Result<PhoneChangeChallenge> = {
        Result.success(PhoneChangeChallenge(challengeId = A_CHALLENGE_ID, otpExpiresInSeconds = 300))
    },
    private val completePhoneChangeResult: () -> Result<Unit> = { Result.success(Unit) },
    private val passkeyEnrollmentResult: () -> Result<String> = { Result.success(AN_ENROLL_URL) },
) : IdentityServiceClient {
    /** The reauth OTPs requested, in order. Each one is an SMS to the number already on file. */
    val startReauthCalls: MutableList<String?> = mutableListOf()

    /** Every phone-change start, in order. This is the only call that can text the NEW number. */
    val startPhoneChangeCalls: MutableList<StartCall> = mutableListOf()

    val completePhoneChangeCalls: MutableList<Pair<String, String>> = mutableListOf()

    val passkeyEnrollmentCalls: MutableList<String> = mutableListOf()

    /** Every initial-PIN call, in order. The server refuses this one when a PIN already exists. */
    val setInitialPinCalls: MutableList<String> = mutableListOf()

    data class StartCall(
        val reauthToken: String,
        val newPhone: String,
        val pin: String?,
        val passkeyStepUpId: String?,
        val passkeyCredentialJson: String?,
    )

    override suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>> =
        Result.success(emptyList())

    override suspend fun accountFactorStatus(accessToken: String, userId: String): Result<AccountFactorStatus> =
        factorStatusResult()

    override suspend fun setInitialPin(accessToken: String, userId: String, newPin: String): Result<Unit> {
        setInitialPinCalls += newPin
        return Result.success(Unit)
    }

    override suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String> =
        Result.success("pin-change-challenge")

    override suspend fun completePinChange(accessToken: String, challengeId: String, otpCode: String, newPin: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun startPhoneChangeReauth(accessToken: String, language: String?): Result<Unit> {
        startReauthCalls += language
        return startReauthResult()
    }

    override suspend fun verifyPhoneChangeReauth(accessToken: String, code: String): Result<String> =
        verifyReauthResult()

    override suspend fun startPhoneChange(
        accessToken: String,
        reauthToken: String,
        newPhone: String,
        pin: String?,
        passkeyStepUpId: String?,
        passkeyCredentialJson: String?,
        language: String?,
    ): Result<PhoneChangeChallenge> {
        startPhoneChangeCalls += StartCall(
            reauthToken = reauthToken,
            newPhone = newPhone,
            pin = pin,
            passkeyStepUpId = passkeyStepUpId,
            passkeyCredentialJson = passkeyCredentialJson,
        )
        return startPhoneChangeResult()
    }

    override suspend fun completePhoneChange(accessToken: String, challengeId: String, code: String): Result<Unit> {
        completePhoneChangeCalls += challengeId to code
        return completePhoneChangeResult()
    }

    override suspend fun startPasskeyEnrollment(accessToken: String): Result<String> {
        passkeyEnrollmentCalls += accessToken
        return passkeyEnrollmentResult()
    }

    override suspend fun registerAccountGenesis(genesisB64Url: String, proofB64Url: String): Result<AccountGenesisRegistration> =
        Result.success(AccountGenesisRegistration(accountId = "an-account-id", attachHandle = "an-attach-handle"))

    companion object {
        const val A_REAUTH_TOKEN = "a-reauth-token"
        const val A_CHALLENGE_ID = "a-phone-change-challenge"
        const val AN_ENROLL_URL = "https://idp.example.org/passkey/enroll?token=abc"
    }
}

/** An [AccountFactorStatus] whose derived fields stay consistent with the factors it is given. */
fun aFactorStatus(
    hasPin: Boolean = true,
    passkeyRegistered: Boolean = false,
    phoneChangeStepUpFactors: List<AuthFactor> = listOf(AuthFactor.PASSKEY, AuthFactor.PIN),
    changePhoneCooldownRemainingSeconds: Long = 0,
) = AccountFactorStatus(
    hasPin = hasPin,
    passkeyRegistered = passkeyRegistered,
    preferredFactor = when {
        passkeyRegistered -> AuthFactor.PASSKEY
        hasPin -> AuthFactor.PIN
        else -> AuthFactor.PHONE_OTP
    },
    phoneChangeStepUpFactors = phoneChangeStepUpFactors,
    changePhoneCooldownRemainingSeconds = changePhoneCooldownRemainingSeconds,
)
