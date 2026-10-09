/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.AccountGenesisRegistration
import io.element.android.libraries.guaresolver.ContactMatch
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.PhoneChangeChallenge
import io.element.android.tests.testutils.lambda.lambdaError

/**
 * An [IdentityServiceClient] for contact discovery. Only the lookup is expected; anything else fails the test.
 */
class FakeIdentityServiceClient(
    private val lookupContactsResult: (List<String>) -> Result<List<ContactMatch>> = { Result.success(emptyList()) },
) : IdentityServiceClient {
    data class LookupCall(val accessToken: String, val phones: List<String>)

    val lookupCalls = mutableListOf<LookupCall>()

    override suspend fun lookupContacts(accessToken: String, phones: List<String>): Result<List<ContactMatch>> {
        lookupCalls += LookupCall(accessToken, phones)
        return lookupContactsResult(phones)
    }

    override suspend fun accountFactorStatus(accessToken: String, userId: String): Result<AccountFactorStatus> = lambdaError()

    override suspend fun cancelAccountRecovery(accessToken: String): Result<Unit> = lambdaError()

    override suspend fun startPinEnrollment(accessToken: String): Result<String> = lambdaError()

    override suspend fun startPinChange(accessToken: String, phone: String, currentPin: String): Result<String> = lambdaError()

    override suspend fun completePinChange(accessToken: String, challengeId: String, otpCode: String, newPin: String): Result<Unit> =
        lambdaError()

    override suspend fun startPhoneChangeReauth(accessToken: String, phone: String, language: String?): Result<Unit> = lambdaError()

    override suspend fun verifyPhoneChangeReauth(accessToken: String, phone: String, code: String): Result<String> = lambdaError()

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
