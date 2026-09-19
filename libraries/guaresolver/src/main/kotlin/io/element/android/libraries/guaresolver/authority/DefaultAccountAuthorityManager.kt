/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.guaresolver.genesis.AccountAuthorityKeyStore
import io.element.android.libraries.guaresolver.genesis.AccountId
import io.element.android.libraries.guaresolver.genesis.Base64Url
import timber.log.Timber

/**
 * GUA FORK: default [AccountAuthorityManager].
 *
 * The order of operations is the security property, and it is the same for every transition: ask for the
 * challenge (which is also where the step-up is spent), build the canonical bytes, sign
 * `magic || challenge || bytes`, then submit the record with the challenge it signed. A client that signed
 * first and asked for a challenge afterwards would be producing exactly the precomputable, transferable
 * artifact ADM-009 decision 2 exists to rule out.
 *
 * The accountId is never composed here. It is read from `GET /account/authority`, which is the one endpoint
 * that returns it, and parsed rather than trusted: [AccountId.parse] re-encodes and compares, so a value
 * that is not the account's own canonical spelling never reaches the bytes that get signed.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAccountAuthorityManager(
    private val client: AccountAuthorityClient,
    private val keyStore: AccountAuthorityKeyStore,
) : AccountAuthorityManager {
    override suspend fun state(accessToken: String): Result<AuthorityChainState> = client.state(accessToken)

    override suspend fun holdsAuthority(): Boolean = keyStore.authorityDevicePublicKey() != null

    override suspend fun beginAdoption(): Result<AdoptionOffer> = runCatchingExceptions {
        AdoptionOffer(recoveryArtifact = keyStore.createAdoptionKeys().recoveryArtifact)
    }.onFailure { error ->
        // Never with the value: what failed here is key material being sealed.
        Timber.w("Could not prepare an account authority adoption: %s", error.javaClass.simpleName)
    }

    override suspend fun adopt(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        pin: String?,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> {
        if (!artifactConfirmed) {
            // The same refusal the server gives, raised before a challenge is spent. ADM-009 decision 7
            // makes the artifact the only way back from a lost device, so an adoption that did not show it
            // must not be reachable, from either side.
            return Result.failure(AuthorityError.ArtifactUnconfirmed)
        }
        val keys = keyStore.adoptionKeys()
            ?: return Result.failure(IllegalStateException("No adoption keys are stored on this device"))
        val accountReference = accountReference(chain) ?: return Result.failure(AuthorityError.NoAccount)

        return client.challenge(accessToken, AuthorityPurpose.ADOPT, pin).mapCatchingExceptions { challenge ->
            val challengeBytes = Base64Url.decode(challenge.challengeB64Url)
            val record = AuthorityRecordCodec.adoptRoot(
                accountReference = accountReference,
                deviceKey = keys.deviceAuthorityPublicKey(),
                recoveryAuthorityKey = keys.recoveryAuthorityPublicKey(),
                label = deviceLabel,
            )
            val signature = keyStore.signWithAdoptionKey(
                AuthorityProofs.recordPreimage(AuthorityRecordType.ADOPT_ROOT, challengeBytes, record)
            )
            val submitted = client.adopt(
                accessToken = accessToken,
                submission = AuthorityRecordSubmission(
                    recordB64Url = Base64Url.encode(record),
                    signatureB64Url = Base64Url.encode(signature),
                    challengeB64Url = challenge.challengeB64Url,
                    recoveryArtifactConfirmed = true,
                ),
            ).getOrThrow()
            // The pair is this account's authority now, even while the record is pending: it holds the slot
            // at seq 1, and a second adoption on this device would mint keys the chain has no place for.
            keyStore.markAdopted(chain.accountId)
            submitted
        }
    }

    override suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit> =
        client.oppose(accessToken, recordHash, pin)

    override suspend fun grantDevice(
        accessToken: String,
        chain: AuthorityChainState,
        granteeDeviceKeyB64Url: String,
        label: String,
        pin: String?,
    ): Result<AuthoritySubmission> {
        val accountReference = accountReference(chain) ?: return Result.failure(AuthorityError.NoAccount)
        val authorizingKey = keyStore.authorityDevicePublicKey()
            ?: return Result.failure(AuthorityError.SignerRefused)
        val granteeKey = runCatchingExceptions { Base64Url.decode(granteeDeviceKeyB64Url) }.getOrNull()
            ?: return Result.failure(
                InvalidAuthorityRecordException("invalid_device_key", "the grantee key is not base64url")
            )

        return client.challenge(accessToken, AuthorityPurpose.GRANT, pin).mapCatchingExceptions { challenge ->
            val challengeBytes = Base64Url.decode(challenge.challengeB64Url)
            val record = AuthorityRecordCodec.deviceGrant(
                accountReference = accountReference,
                // The grant appends to the chain as the server last reported it. A head that moved under it
                // is refused as authority_head_conflict rather than merged, which is what "one head, one
                // order, no races" means for the client: re-read and decide again.
                prevHash = AuthorityRecordCodec.prevHashFromHex(chain.headHash),
                seq = chain.headSeq + 1,
                granteeDeviceKey = granteeKey,
                label = label,
                authorizingKey = authorizingKey,
            )
            val signature = keyStore.signAsAuthorityDevice(
                AuthorityProofs.recordPreimage(AuthorityRecordType.DEVICE_GRANT, challengeBytes, record)
            )
            client.grantDevice(
                accessToken = accessToken,
                submission = AuthorityRecordSubmission(
                    recordB64Url = Base64Url.encode(record),
                    signatureB64Url = Base64Url.encode(signature),
                    challengeB64Url = challenge.challengeB64Url,
                ),
            ).getOrThrow()
        }
    }

    override suspend fun approvals(accessToken: String): Result<List<AuthorityApproval>> =
        client.liveApprovals(accessToken)

    override suspend fun approve(
        accessToken: String,
        chain: AuthorityChainState,
        approval: AuthorityApproval,
    ): Result<Unit> {
        val accountReference = accountReference(chain) ?: return Result.failure(AuthorityError.NoAccount)
        return runCatchingExceptions {
            val preimage = AuthorityProofs.approvalPreimage(
                accountReference = accountReference,
                approvalId = Base64Url.decode(approval.approvalId),
                actionDigest = Base64Url.decode(approval.actionDigestB64Url),
                challenge = Base64Url.decode(approval.challengeB64Url),
            )
            val signature = keyStore.signAsAuthorityDevice(preimage)
            client.signApproval(accessToken, approval.approvalId, Base64Url.encode(signature)).getOrThrow()
        }
    }

    /**
     * The 34 raw bytes of the accountId the server reported, or null when it is not a canonical accountId.
     *
     * Parsed rather than decoded loosely: a value this client could not re-encode to the same string is not
     * this account's id, and signing over 34 bytes derived from it would produce a record refused for a
     * reason nobody could read off the request.
     */
    private fun accountReference(chain: AuthorityChainState): ByteArray? =
        runCatchingExceptions { AccountId.parse(chain.accountId).rawBytes() }.getOrNull()
}
