/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

import io.element.android.libraries.guaresolver.genesis.AccountGenesisManager
import io.element.android.libraries.guaresolver.genesis.GenesisRegistration

/**
 * GUA FORK: lambda-overridable fake [AccountGenesisManager] that also counts registrations, so a test
 * can assert that nothing ran while the feature flag was off.
 */
class FakeAccountGenesisManager(
    private val registerResult: () -> GenesisRegistration = { GenesisRegistration.Unavailable },
    private val signAttachProofResult: (String) -> Result<String> = { Result.success("signature") },
) : AccountGenesisManager {
    var registerCount: Int = 0
        private set

    override suspend fun registerForSignup(): GenesisRegistration {
        registerCount++
        return registerResult()
    }

    override suspend fun signAttachProof(challengeB64Url: String): Result<String> = signAttachProofResult(challengeB64Url)
}
