/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * The client half of account genesis: generates the account authority key, registers the genesis it
 * commits, and signs the attach proof the sign-in page asks for.
 *
 * Nothing here runs unless the caller has checked the account-genesis feature flag.
 */
interface AccountGenesisManager {
    /**
     * Generates the authority key if needed, mints a genesis over it and registers it, all before the
     * OIDC flow starts.
     *
     * Returns [GenesisRegistration.Registered] with the single-use handle to put in the `login_hint`,
     * [GenesisRegistration.Unavailable] when the deployment does not do genesis (the signup continues
     * unchanged), or [GenesisRegistration.Failed] when the client meant to register one and could not.
     * That must fail the signup rather than silently create an account with no genesis.
     */
    suspend fun registerForSignup(): GenesisRegistration

    /**
     * Signs the attach proof for the challenge the profile step issued.
     *
     * @param challengeB64Url the 32 server-chosen bytes, base64url without padding, as issued.
     * @return the 64-byte signature as base64url without padding.
     */
    suspend fun signAttachProof(challengeB64Url: String): Result<String>
}

/** The outcome of registering a genesis for one signup. */
sealed interface GenesisRegistration {
    /**
     * The genesis is registered and the handle is good for this signup only, until it expires or is
     * burned by a successful attach.
     */
    data class Registered(
        val accountId: AccountId,
        val attachHandle: String,
    ) : GenesisRegistration

    /** The deployment does not issue account genesis. The signup continues with no handle and nothing shown to the user. */
    data object Unavailable : GenesisRegistration

    /** The client meant to register a genesis and could not. The signup must fail. */
    data class Failed(val error: Throwable) : GenesisRegistration
}
