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
            // Adopted while the record is still pending: it already holds the slot at seq 1.
            keyStore.markAdopted(chain.accountId)
        }
    }

    override suspend fun startWebStepUp(
        accessToken: String,
        purpose: AuthorityPurpose,
    ): Result<String> {
        if (purpose !in WEB_STEP_UP_PURPOSES) {
            return Result.failure(AuthorityError.StepUpPurposeRefused)
        }
        return client.startWebStepUp(accessToken, purpose)
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
        val opposedPrevHash = pending.prevHash
            ?.let { runCatchingExceptions { AuthorityRecordCodec.prevHashFromHex(it) }.getOrNull() }
            ?: return Result.failure(AuthorityError.OppositionStale)

        return submit(
            accessToken = accessToken,
            purpose = AuthorityPurpose.OPPOSE,
            stepUp = AuthorityStepUp.None,
            type = AuthorityRecordType.OPPOSE,
            build = {
                AuthorityRecordCodec.oppose(
                    accountReference = accountReference,
                    prevHash = opposedPrevHash,
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
            return Result.failure(AuthorityError.ArtifactUnconfirmed)
        }
        val accountReference = accountReference(chain) ?: return Result.failure(AuthorityError.NoAccount)
        val keys = keyStore.adoptionKeys()
            ?: return Result.failure(IllegalStateException("No recovery keys are stored on this device"))
        val oldRecoverySeed = runCatchingExceptions { RecoveryArtifact.decode(recoveryArtifact) }
            .getOrElse { error -> return Result.failure(error) }
        val oldRecoveryKey = Ed25519Sign.KeyPair.newKeyPairFromSeed(oldRecoverySeed).publicKey
        if (MessageDigest.isEqual(oldRecoveryKey, keys.recoveryAuthorityPublicKey())) {
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

    override suspend fun beginAccountRecovery(): Result<AdoptionOffer> = runCatchingExceptions {
        AdoptionOffer(recoveryArtifact = keyStore.createAdoptionKeys().recoveryArtifact)
    }.onFailure { error ->
        Timber.w("Could not prepare an account-recovery authority record: %s", error.javaClass.simpleName)
    }

    override suspend fun recoverThroughAccountRecovery(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission> {
        if (!artifactConfirmed) {
            return Result.failure(AuthorityError.ArtifactUnconfirmed)
        }
        val accountReference = accountReference(chain) ?: return Result.failure(AuthorityError.NoAccount)
        val keys = keyStore.adoptionKeys()
            ?: return Result.failure(IllegalStateException("No recovery keys are stored on this device"))

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
                    authorization = AuthorityRecord.AUTHORIZATION_ACCOUNT_RECOVERY,
                    authorizingKey = null,
                )
            },
            sign = { preimage -> keyStore.signWithAdoptionKey(preimage) },
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
        val rows = client.securityNotifications(accessToken).getOrNull().orEmpty()
        val bound = rows.firstOrNull { it.installationId == installationId }?.boundToAnAuthorityDevice == true
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
                pin = pin,
                challengeB64Url = signed?.first,
                signatureB64Url = signed?.second,
            ),
        ).getOrThrow()
    }

    override suspend fun installationId(): String = keyStore.installationId()

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
            Timber.w("Could not record this device's granted authority: %s", error.javaClass.simpleName)
        }
    }

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
        // Self-check before signing: a record the server refuses costs the user another step-up.
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

    private fun accountReference(chain: AuthorityChainState): ByteArray? =
        runCatchingExceptions { AccountId.parse(chain.accountId).rawBytes() }.getOrNull()

    private companion object {
        private val NO_SUBMISSION = Unit

        private val WEB_STEP_UP_PURPOSES = setOf(
            AuthorityPurpose.ADOPT,
            AuthorityPurpose.GRANT,
            AuthorityPurpose.REVOKE,
            AuthorityPurpose.RECOVER,
        )
    }
}
