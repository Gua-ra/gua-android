/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.ResolverError
import timber.log.Timber

/**
 * GUA FORK: default [AccountGenesisManager].
 *
 * The accountId this holds is the one the SERVER derived and returned. It is compared against the one
 * derived here from the same bytes, so a disagreement is caught at registration rather than at the
 * attach step, where it would only surface as a proof that does not verify.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAccountGenesisManager(
    private val keyStore: AccountAuthorityKeyStore,
    private val identityServiceClient: IdentityServiceClient,
) : AccountGenesisManager {
    @Volatile
    private var registeredAccountId: AccountId? = null

    override suspend fun registerForSignup(): GenesisRegistration {
        return try {
            // A fresh pair per signup: re-using one that already owns an account would be an attempt to
            // re-point it, which the server refuses with a conflict anyway.
            val keys = keyStore.createKeyPair()
            val canonicalBytes = AccountGenesisCodec.mint(
                authorityPublicKey = keys.authorityPublicKey(),
                recoveryAuthorityPublicKey = keys.recoveryAuthorityPublicKey(),
            )
            // Decode what was just built, so a mint that somehow produced bytes the server would refuse
            // fails here rather than as an opaque 400.
            val genesis = AccountGenesisCodec.decode(canonicalBytes)
            val derivedAccountId = genesis.accountId()
            val proof = keyStore.signWithAuthorityKey(GenesisProofs.genesisProofPreimage(canonicalBytes))

            val registration = identityServiceClient.registerAccountGenesis(
                genesisB64Url = Base64Url.encode(canonicalBytes),
                proofB64Url = Base64Url.encode(proof),
            ).getOrElse { error ->
                return when {
                    isGenesisUnsupported(error) -> {
                        // Not a failure: the deployment does not do genesis, so this signup takes the
                        // bootstrap branch with no handle and nothing shown to the user.
                        Timber.i("This deployment does not issue an account genesis; continuing without one")
                        keyStore.clear()
                        GenesisRegistration.Unavailable
                    }
                    else -> GenesisRegistration.Failed(error)
                }
            }

            if (registration.accountId != derivedAccountId.value) {
                return GenesisRegistration.Failed(
                    IllegalStateException("The registered accountId does not match the one derived from the same bytes")
                )
            }
            if (!GuaLoginHint.isValidAttachHandle(registration.attachHandle)) {
                return GenesisRegistration.Failed(IllegalStateException("The attach handle is not a well-formed value"))
            }
            registeredAccountId = derivedAccountId
            GenesisRegistration.Registered(accountId = derivedAccountId, attachHandle = registration.attachHandle)
        } catch (error: Throwable) {
            // Never log the error with any key material in it.
            Timber.w("Could not register an account genesis for this signup")
            GenesisRegistration.Failed(error)
        }
    }

    override suspend fun signAttachProof(challengeB64Url: String): Result<String> {
        return runCatchingExceptions {
            val accountId = registeredAccountId ?: error("No account genesis was registered in this signup")
            val challenge = Base64Url.decode(challengeB64Url)
            check(challenge.size == GenesisProofs.ATTACH_CHALLENGE_LENGTH) {
                "The attach challenge is not ${GenesisProofs.ATTACH_CHALLENGE_LENGTH} bytes"
            }
            val preimage = GenesisProofs.attachProofPreimage(challenge, accountId)
            Base64Url.encode(keyStore.signWithAuthorityKey(preimage))
        }
    }

    /**
     * True when the deployment said it does not do account genesis.
     *
     * 503 is the documented answer while `identity.genesis.enabled` is off. 403 is the answer while the
     * deployment declines to issue under recovery framework 0x01, which ADM-008 decision 4 gates on
     * ADM-002. Both mean no handle exists to present, which is the no-handle bootstrap branch decision 6
     * calls "not a failure", so both continue silently rather than blocking the signup.
     */
    private fun isGenesisUnsupported(error: Throwable): Boolean =
        error is ResolverError.Server && (error.status == 503 || error.status == 403)
}
