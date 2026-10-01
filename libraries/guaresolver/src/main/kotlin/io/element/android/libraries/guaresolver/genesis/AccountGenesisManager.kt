/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

interface AccountGenesisManager {
    /** [GenesisRegistration.Failed] must fail the signup. [GenesisRegistration.Unavailable] continues it unchanged. */
    suspend fun registerForSignup(): GenesisRegistration

    suspend fun signAttachProof(challengeB64Url: String): Result<String>
}

sealed interface GenesisRegistration {
    data class Registered(
        val accountId: AccountId,
        val attachHandle: String,
    ) : GenesisRegistration

    data object Unavailable : GenesisRegistration

    data class Failed(val error: Throwable) : GenesisRegistration
}
