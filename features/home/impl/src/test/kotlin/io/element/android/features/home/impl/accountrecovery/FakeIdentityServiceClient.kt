/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.accountrecovery

import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.AccountGenesisRegistration
import io.element.android.libraries.guaresolver.AuthFactor
import io.element.android.libraries.guaresolver.ContactMatch
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.PhoneChangeChallenge
import io.element.android.tests.testutils.lambda.lambdaError

/**
 * GUA FORK: an [IdentityServiceClient] for the account recovery banner. Only the status read and the
 * cancel are expected; anything else fails the test.
 */
class FakeIdentityServiceClient(
    private val accountFactorStatusResult: suspend (String, String) -> Result<AccountFactorStatus> = { _, _ -> lambdaError() },
    private val cancelAccountRecoveryResult: suspend (String) -> Result<Unit> = { lambdaError() },
) : IdentityServiceClient {
    override suspend fun accountFactorStatus(accessToken: String, userId: String): Result<AccountFactorStatus> =
        accountFactorStatusResult(accessToken, userId)

    override suspend fun cancelAccountRecovery(accessToken: String): Result<Unit> =
        cancelAccountRecoveryResult(accessToken)

    override suspend fun lookupContacts(accessToken: String, hashedPhones: List<String>): Result<List<ContactMatch>> = lambdaError()

    override suspend fun setInitialPin(accessToken: String, userId: String, newPin: String): Result<Unit> = lambdaError()

    override suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String> = lambdaError()

    override suspend fun completePinChange(accessToken: String, challengeId: String, otpCode: String, newPin: String): Result<Unit> =
        lambdaError()

    override suspend fun startPhoneChangeReauth(accessToken: String, language: String?): Result<Unit> = lambdaError()

    override suspend fun verifyPhoneChangeReauth(accessToken: String, code: String): Result<String> = lambdaError()

    override suspend fun startPhoneChange(
        accessToken: String,
        reauthToken: String,
        newPhone: String,
        pin: String?,
        passkeyStepUpId: String?,
        passkeyCredentialJson: String?,
        language: String?,
    ): Result<PhoneChangeChallenge> = lambdaError()

    override suspend fun completePhoneChange(accessToken: String, challengeId: String, code: String): Result<Unit> = lambdaError()

    override suspend fun startPasskeyEnrollment(accessToken: String): Result<String> = lambdaError()

    override suspend fun registerAccountGenesis(genesisB64Url: String, proofB64Url: String): Result<AccountGenesisRegistration> =
        lambdaError()
}

/** An account holding a PIN, with or without a live delayed recovery. */
fun aRecoveryStatus(
    pending: Boolean,
    completableAtEpochSeconds: Long? = if (pending) A_COMPLETABLE_AT_EPOCH_SECONDS else null,
) = AccountFactorStatus(
    hasPin = true,
    passkeyRegistered = false,
    preferredFactor = AuthFactor.PIN,
    phoneChangeStepUpFactors = listOf(AuthFactor.PASSKEY, AuthFactor.PIN),
    changePhoneCooldownRemainingSeconds = 0,
    accountRecoveryPending = pending,
    accountRecoveryCompletableAtEpochSeconds = completableAtEpochSeconds,
    accountRecoveryExpiresAtEpochSeconds = completableAtEpochSeconds?.plus(7 * 24 * 3600L),
)

const val A_COMPLETABLE_AT_EPOCH_SECONDS = 1_790_000_000L
