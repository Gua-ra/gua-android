/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import com.google.crypto.tink.subtle.Ed25519Sign
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.guaresolver.genesis.AccountAuthorityKeyStore
import io.element.android.libraries.guaresolver.genesis.AccountId
import io.element.android.libraries.guaresolver.genesis.Base64Url
import timber.log.Timber
import java.security.MessageDigest

/**
 * GUA FORK: default [AccountAuthorityManager].
 *
 * The order of operations is the security property, and it is the same for every transition: ask for the
 * challenge (which is also where the step-up is spent), build the canonical bytes, sign
 * `magic || challenge || bytes`, then submit the record with the challenge it signed. A client that signed
 * first and asked for a challenge afterwards would be producing exactly the precomputable, transferable
 * artifact ADM-009 decision 2 exists to rule out. [submit] is that order, written once, so no transition can
 * quietly get a different one.
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
    override suspend fun state(accessToken: String): Result<AuthorityChainState> =
        client.state(accessToken).onSuccess { chain -> adoptGrantedCandidate(chain) }

    override suspend fun holdsAuthority(): Boolean = keyStore.authorityDevicePublicKey() != null

    override suspend fun authorityDeviceKeyB64Url(): String? =
        keyStore.authorityDevicePublicKey()?.let { Base64Url.encode(it) }

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
        stepUp: AuthorityStepUp,
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

        return submit(
            accessToken = accessToken,
            purpose = AuthorityPurpose.ADOPT,
            stepUp = stepUp,
            type = AuthorityRecordType.ADOPT_ROOT,
            build = {
                AuthorityRecordCodec.adoptRoot(
                    accountReference = accountReference,
                    deviceKey = keys.deviceAuthorityPublicKey(),
                    recoveryAuthorityKey = keys.recoveryAuthorityPublicKey(),
                    label = deviceLabel,
                )
            },
            sign = { preimage -> keyStore.signWithAdoptionKey(preimage) },
            send = { submission -> client.adopt(accessToken, submission.copy(recoveryArtifactConfirmed = true)) },
        ).onSuccess {
            // The pair is this account's authority now, even while the record is pending: it holds the slot
            // at seq 1, and a second adoption on this device would mint keys the chain has no place for.
            keyStore.markAdopted(chain.accountId)
        }
    }

    override suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit> =
        client.oppose(accessToken, recordHash, pin)

    override suspend fun opposeWithRecord(accessToken: String, chain: AuthorityChainState): Result<Unit> {
        val pending = chain.pending ?: return Result.failure(AuthorityError.OppositionStale)
        val accountReference = accountReference(chain) ?: return Result.failure(AuthorityError.NoAccount)
        val authorizingKey = keyStore.authorityDevicePublicKey()
            ?: return Result.failure(AuthorityError.OppositionDeviceRequired)
        val opposedHash = runCatchingExceptions { AuthorityRecordCodec.prevHashFromHex(pending.recordHash) }
            .getOrNull()
            ?: return Result.failure(AuthorityError.OppositionStale)

        return submit(
            accessToken = accessToken,
            // No factor: an Oppose asks for none, and the server's step-up service returns early for this
            // purpose rather than trusting the client to send nothing.
            purpose = AuthorityPurpose.OPPOSE,
            stepUp = AuthorityStepUp.None,
            type = AuthorityRecordType.OPPOSE,
            build = {
                AuthorityRecordCodec.oppose(
                    accountReference = accountReference,
                    // A pending record holds its seq without being appended, so the head this client just
                    // read is still the one that record names as its own prevHash.
                    prevHash = AuthorityRecordCodec.prevHashFromHex(chain.headHash),
                    seq = pending.seq,
                    opposedRecordHash = opposedHash,
                    authorizingKey = authorizingKey,
                )
            },
            sign = { preimage -> keyStore.signAsAuthorityDevice(preimage) },
            send = { submission -> client.opposeWithRecord(accessToken, submission).map { NO_SUBMISSION } },
        ).map { }
    }

    override suspend fun offerThisDeviceForGrant(
        accessToken: String,
        label: String,
    ): Result<AuthorityCandidate> = runCatchingExceptions {
        // A device that already holds authority has nothing to offer, and offering would mint a second key
        // for the same phone that the chain has no place for.
        val existing = keyStore.authorityDevicePublicKey()
        if (existing != null) {
            throw AuthorityError.PositionRefused
        }
        val candidateKey = keyStore.candidateDevicePublicKey() ?: keyStore.createCandidateKey()
        client.offerCandidate(accessToken, Base64Url.encode(candidateKey), label).getOrThrow()
    }

    override suspend fun candidates(accessToken: String): Result<List<AuthorityCandidate>> =
        client.candidates(accessToken)

    override suspend fun grantDevice(
        accessToken: String,
        chain: AuthorityChainState,
        candidate: AuthorityCandidate,
        stepUp: AuthorityStepUp,
        fingerprintConfirmed: Boolean,
    ): Result<AuthoritySubmission> {
        if (!fingerprintConfirmed) {
            // The comparison is the binding. A grant that skipped it is a grant over whatever key came up
            // the channel, which is the one thing decision 5's candidate step exists to stop.
            return Result.failure(AuthorityError.SignerRefused)
        }
        val accountReference = accountReference(chain) ?: return Result.failure(AuthorityError.NoAccount)
        val authorizingKey = keyStore.authorityDevicePublicKey()
            ?: return Result.failure(AuthorityError.SignerRefused)
        val granteeKey = runCatchingExceptions { Base64Url.decode(candidate.deviceKeyB64Url) }.getOrNull()
            ?: return Result.failure(
                InvalidAuthorityRecordException("invalid_device_key", "the grantee key is not base64url")
            )
        val recomputed = runCatchingExceptions { AuthorityFingerprint.of(granteeKey) }.getOrNull()
        if (recomputed == null || recomputed != candidate.fingerprint) {
            // The person confirmed eight characters. If those characters are not the ones these 32 bytes
            // produce, they confirmed something else.
            return Result.failure(AuthorityError.UnknownCandidate)
        }

        return submit(
            accessToken = accessToken,
            purpose = AuthorityPurpose.GRANT,
            stepUp = stepUp,
            type = AuthorityRecordType.DEVICE_GRANT,
            build = {
                AuthorityRecordCodec.deviceGrant(
                    accountReference = accountReference,
                    // The grant appends to the chain as the server last reported it. A head that moved under
                    // it is refused as authority_head_conflict rather than merged, which is what "one head,
                    // one order, no races" means for the client: re-read and decide again.
                    prevHash = AuthorityRecordCodec.prevHashFromHex(chain.headHash),
                    seq = chain.headSeq + 1,
                    granteeDeviceKey = granteeKey,
                    label = candidate.label,
                    authorizingKey = authorizingKey,
                )
            },
            sign = { preimage -> keyStore.signAsAuthorityDevice(preimage) },
            send = { submission -> client.grantDevice(accessToken, submission) },
        )
    }

    override suspend fun revokeDevice(
        accessToken: String,
        chain: AuthorityChainState,
        deviceKeyB64Url: String,
        reason: Int,
        stepUp: AuthorityStepUp,
    ): Result<AuthoritySubmission> {
        val accountReference = accountReference(chain) ?: return Result.failure(AuthorityError.NoAccount)
        val authorizingKey = keyStore.authorityDevicePublicKey()
            ?: return Result.failure(AuthorityError.SignerRefused)
        val targetKey = runCatchingExceptions { Base64Url.decode(deviceKeyB64Url) }.getOrNull()
            ?: return Result.failure(
                InvalidAuthorityRecordException("invalid_device_key", "the device key is not base64url")
            )

        return submit(
            accessToken = accessToken,
            purpose = AuthorityPurpose.REVOKE,
            stepUp = stepUp,
            type = AuthorityRecordType.DEVICE_REVOKE,
            build = {
                AuthorityRecordCodec.deviceRevoke(
                    accountReference = accountReference,
                    prevHash = AuthorityRecordCodec.prevHashFromHex(chain.headHash),
                    seq = chain.headSeq + 1,
                    deviceKey = targetKey,
                    reason = reason,
                    authorizingKey = authorizingKey,
                )
            },
            sign = { preimage -> keyStore.signAsAuthorityDevice(preimage) },
            send = { submission -> client.revokeDevice(accessToken, submission) },
        )
    }

    override suspend fun beginRecovery(recoveryArtifact: String): Result<AdoptionOffer> =
        runCatchingExceptions {
            // Parsed first, so a typo costs nothing. The seed itself is not kept here: it is read again from
            // the same string when the record is signed, and nothing writes it anywhere.
            RecoveryArtifact.decode(recoveryArtifact)
            AdoptionOffer(recoveryArtifact = keyStore.createAdoptionKeys().recoveryArtifact)
        }.onFailure { error ->
            Timber.w("Could not prepare an authority recovery: %s", error.javaClass.simpleName)
        }

    override suspend fun recoverAuthority(
        accessToken: String,
        chain: AuthorityChainState,
        recoveryArtifact: String,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> {
        if (!artifactConfirmed) {
            // The NEW artifact this record commits. A recovery that did not hand one over would leave the
            // account one lost device away from the terminal state with nothing to come back with.
            return Result.failure(AuthorityError.ArtifactUnconfirmed)
        }
        val accountReference = accountReference(chain) ?: return Result.failure(AuthorityError.NoAccount)
        val keys = keyStore.adoptionKeys()
            ?: return Result.failure(IllegalStateException("No recovery keys are stored on this device"))
        val oldRecoverySeed = runCatchingExceptions { RecoveryArtifact.decode(recoveryArtifact) }
            .getOrElse { error -> return Result.failure(error) }
        val oldRecoveryKey = Ed25519Sign.KeyPair.newKeyPairFromSeed(oldRecoverySeed).publicKey
        if (MessageDigest.isEqual(oldRecoveryKey, keys.recoveryAuthorityPublicKey())) {
            // The record would then commit the key that authorized it, which leaves the account exactly where
            // it was: one artifact, already written down somewhere, still the only way back.
            return Result.failure(
                InvalidAuthorityRecordException("duplicate_keys", "the new recovery key repeats the old one")
            )
        }

        return submit(
            accessToken = accessToken,
            purpose = AuthorityPurpose.RECOVER,
            stepUp = stepUp,
            type = AuthorityRecordType.AUTHORITY_RECOVERY,
            build = {
                AuthorityRecordCodec.authorityRecovery(
                    accountReference = accountReference,
                    prevHash = AuthorityRecordCodec.prevHashFromHex(chain.headHash),
                    seq = chain.headSeq + 1,
                    deviceKey = keys.deviceAuthorityPublicKey(),
                    recoveryAuthorityKey = keys.recoveryAuthorityPublicKey(),
                    label = deviceLabel,
                    // Authorization 0x01, the rank-2 record. The weaker account-recovery path is the server's
                    // to authorize from a completed recovery, and this client has no way to produce it: it
                    // would mean submitting a record signed by the device key it installs, which only makes
                    // sense once the server has a completion stamp to weigh it against.
                    authorization = AuthorityRecord.AUTHORIZATION_RECOVERY_KEY,
                    authorizingKey = oldRecoveryKey,
                )
            },
            sign = { preimage -> Ed25519Sign(oldRecoverySeed).sign(preimage) },
            send = { submission ->
                client.recoverAuthority(accessToken, submission.copy(recoveryArtifactConfirmed = true))
            },
        ).onSuccess {
            keyStore.markRecovered(chain.accountId)
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

    override suspend fun registerSecurityNotifications(
        accessToken: String,
        pushToken: String,
        platform: String,
        appId: String,
        deviceLabel: String,
    ): Result<Unit> = runCatchingExceptions {
        val installationId = keyStore.installationId()
        val deviceKey = keyStore.authorityDevicePublicKey()
        val registration = if (deviceKey == null) {
            // An install with no authority key registers unbound. Its own removal still works, because that
            // tier is "the caller names itself"; what it cannot do is be removed from another install without
            // a factor, which is the tier the server enforces.
            SecurityNotificationRegistration(
                installationId = installationId,
                platform = platform,
                token = pushToken,
                appId = appId,
                deviceLabel = deviceLabel,
            )
        } else {
            val chain = client.state(accessToken).getOrThrow()
            val accountReference = accountReference(chain)
                ?: throw AuthorityError.NoAccount
            val challenge = client.challenge(accessToken, AuthorityPurpose.NOTIFY, AuthorityStepUp.None)
                .getOrThrow()
            val preimage = AuthorityProofs.notificationPreimage(
                accountReference = accountReference,
                installationIdHash = sha256(installationId.toByteArray(Charsets.UTF_8)),
                deviceKey = deviceKey,
                challenge = Base64Url.decode(challenge.challengeB64Url),
            )
            SecurityNotificationRegistration(
                installationId = installationId,
                platform = platform,
                token = pushToken,
                appId = appId,
                deviceLabel = deviceLabel,
                authorityDeviceKeyB64Url = Base64Url.encode(deviceKey),
                challengeB64Url = challenge.challengeB64Url,
                signatureB64Url = Base64Url.encode(keyStore.signAsAuthorityDevice(preimage)),
            )
        }
        client.registerSecurityNotification(accessToken, registration).getOrThrow()
    }

    override suspend fun securityNotifications(
        accessToken: String,
    ): Result<List<SecurityNotificationView>> = client.securityNotifications(accessToken)

    override suspend fun removeSecurityNotification(
        accessToken: String,
        installationId: String,
        pin: String?,
    ): Result<Unit> = runCatchingExceptions {
        val callerInstallationId = keyStore.installationId()
        val rows = client.securityNotifications(accessToken).getOrNull().orEmpty()
        val bound = rows.firstOrNull { it.installationId == installationId }?.boundToAnAuthorityDevice == true
        // A bound row needs a signature by the key it names. This device can only produce one for the key it
        // holds, which is exactly the rule: an install cannot sign away a binding that is not its own.
        val signed = if (bound && keyStore.authorityDevicePublicKey() != null) {
            val chain = client.state(accessToken).getOrThrow()
            val accountReference = accountReference(chain) ?: throw AuthorityError.NoAccount
            val challenge = client.challenge(accessToken, AuthorityPurpose.NOTIFY, AuthorityStepUp.None)
                .getOrThrow()
            val preimage = AuthorityProofs.notificationPreimage(
                accountReference = accountReference,
                installationIdHash = sha256(installationId.toByteArray(Charsets.UTF_8)),
                deviceKey = requireNotNull(keyStore.authorityDevicePublicKey()),
                challenge = Base64Url.decode(challenge.challengeB64Url),
            )
            challenge.challengeB64Url to Base64Url.encode(keyStore.signAsAuthorityDevice(preimage))
        } else {
            null
        }
        client.removeSecurityNotification(
            accessToken,
            SecurityNotificationRemoval(
                installationId = installationId,
                callerInstallationId = callerInstallationId,
                pin = pin,
                challengeB64Url = signed?.first,
                signatureB64Url = signed?.second,
            ),
        ).getOrThrow()
    }

    override suspend fun installationId(): String = keyStore.installationId()

    /**
     * Promotes this device's candidate key once the chain says the account activated it.
     *
     * A grant is signed by ANOTHER device, so this device never sees the record: what it sees is its own key in
     * the device set, which is the chain saying the key is this account's authority now. Without this step a
     * granted device would hold a key it could never sign with, because nothing else would ever move it out of
     * the candidate slot.
     *
     * A quarantined row promotes too. The device holds authority from acceptance; what the window withholds is
     * what it may sign, and the server enforces that rather than this client pretending the key is not ours.
     */
    private suspend fun adoptGrantedCandidate(chain: AuthorityChainState) {
        runCatchingExceptions {
            val candidate = keyStore.candidateDevicePublicKey() ?: return@runCatchingExceptions
            val offered = Base64Url.encode(candidate)
            val granted = chain.devices.any { device ->
                device.deviceKeyB64Url == offered && (device.isActive || device.isQuarantined)
            }
            if (granted) {
                keyStore.markGranted(chain.accountId)
            }
        }.onFailure { error ->
            // Never with the value: what failed is key material being moved between slots. The read itself
            // still succeeded, and the next one tries again.
            Timber.w("Could not record this device's granted authority: %s", error.javaClass.simpleName)
        }
    }

    /**
     * One transition, in the order ADM-009 fixes, written once.
     *
     * The self-check on the bytes just built is not belt and braces: a record the server's decoder refuses
     * costs a burned challenge, and a burned challenge costs another step-up, so a bug here would show up to
     * the user as "type your PIN again" rather than as a failure.
     */
    private suspend fun <T> submit(
        accessToken: String,
        purpose: AuthorityPurpose,
        stepUp: AuthorityStepUp,
        type: AuthorityRecordType,
        build: () -> ByteArray,
        sign: suspend (ByteArray) -> ByteArray,
        send: suspend (AuthorityRecordSubmission) -> Result<T>,
    ): Result<T> = client.challenge(accessToken, purpose, stepUp).mapCatchingExceptions { challenge ->
        val challengeBytes = Base64Url.decode(challenge.challengeB64Url)
        val record = build()
        AuthorityRecordCodec.parse(record)
        val signature = sign(AuthorityProofs.recordPreimage(type, challengeBytes, record))
        send(
            AuthorityRecordSubmission(
                recordB64Url = Base64Url.encode(record),
                signatureB64Url = Base64Url.encode(signature),
                challengeB64Url = challenge.challengeB64Url,
            )
        ).getOrThrow()
    }

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    /**
     * The 34 raw bytes of the accountId the server reported, or null when it is not a canonical accountId.
     *
     * Parsed rather than decoded loosely: a value this client could not re-encode to the same string is not
     * this account's id, and signing over 34 bytes derived from it would produce a record refused for a
     * reason nobody could read off the request.
     */
    private fun accountReference(chain: AuthorityChainState): ByteArray? =
        runCatchingExceptions { AccountId.parse(chain.accountId).rawBytes() }.getOrNull()

    private companion object {
        /** What an endpoint that answers 204 gives [submit] to carry. */
        private val NO_SUBMISSION = Unit
    }
}
