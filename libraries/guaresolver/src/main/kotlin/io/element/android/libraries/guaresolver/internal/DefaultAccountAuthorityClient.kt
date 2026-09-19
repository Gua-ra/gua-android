/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.authority.AccountAuthorityClient
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityChallenge
import io.element.android.libraries.guaresolver.authority.AuthorityDevice
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityPendingTransition
import io.element.android.libraries.guaresolver.authority.AuthorityPurpose
import io.element.android.libraries.guaresolver.authority.AuthorityRecordSubmission
import io.element.android.libraries.guaresolver.authority.AuthoritySubmission
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber

/**
 * GUA FORK: default [AccountAuthorityClient]. Talks to the active [GuaDeployment]'s identity-service through
 * the app-wide [RetrofitFactory], and maps the server's stable refusal codes onto [AuthorityError].
 *
 * NOTHING IS LOGGED FROM A BODY HERE. A request carries a signature, a challenge and a PIN, and a response
 * carries the account's permanent id; a failure is logged as its code and its status and nothing else.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAccountAuthorityClient(
    private val retrofitFactory: RetrofitFactory,
    private val deployment: GuaDeployment = GuaResolverConfig.current,
) : AccountAuthorityClient {
    override suspend fun challenge(
        accessToken: String,
        purpose: AuthorityPurpose,
        pin: String?,
    ): Result<AuthorityChallenge> = runAuthorityCall { api ->
        val response = api.challenge(
            authorization = bearer(accessToken),
            body = AuthorityChallengeRequest(purpose = purpose.name, pin = pin?.takeIf { it.isNotEmpty() }),
        )
        AuthorityChallenge(
            challengeB64Url = response.challenge,
            expiresInSeconds = response.expiresInSeconds,
        )
    }

    override suspend fun adopt(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission> = runAuthorityCall { api ->
        api.adopt(authorization = bearer(accessToken), body = submission.toRequest()).toSubmission()
    }

    override suspend fun grantDevice(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission> = runAuthorityCall { api ->
        api.grantDevice(authorization = bearer(accessToken), body = submission.toRequest()).toSubmission()
    }

    override suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit> =
        runAuthorityCall { api ->
            api.oppose(
                authorization = bearer(accessToken),
                body = AuthorityOpposeRequest(recordHash = recordHash, pin = pin?.takeIf { it.isNotEmpty() }),
            )
        }

    override suspend fun state(accessToken: String): Result<AuthorityChainState> = runAuthorityCall { api ->
        val response = api.state(authorization = bearer(accessToken))
        AuthorityChainState(
            accountId = response.accountId,
            accountClass = response.accountClass,
            state = response.state,
            headSeq = response.headSeq,
            headHash = response.headHash,
            devices = response.devices.map { device ->
                AuthorityDevice(
                    deviceKeyB64Url = device.deviceKey,
                    label = device.label,
                    state = device.state,
                    quarantineUntilEpochSeconds = device.quarantineUntilEpochSeconds,
                    grantedSeq = device.grantedSeq,
                )
            },
            pending = response.pending?.let { pending ->
                AuthorityPendingTransition(
                    type = pending.type,
                    seq = pending.seq,
                    effectiveAtEpochSeconds = pending.effectiveAtEpochSeconds,
                    recordHash = pending.recordHash,
                )
            },
        )
    }

    override suspend fun liveApprovals(accessToken: String): Result<List<AuthorityApproval>> =
        runAuthorityCall { api ->
            api.liveApprovals(authorization = bearer(accessToken)).map { approval ->
                AuthorityApproval(
                    approvalId = approval.approvalId,
                    code = approval.code,
                    action = approval.action,
                    actionDigestB64Url = approval.actionDigest,
                    challengeB64Url = approval.challenge,
                    expiresAtEpochSeconds = approval.expiresAtEpochSeconds,
                )
            }
        }

    override suspend fun signApproval(
        accessToken: String,
        approvalId: String,
        signatureB64Url: String,
    ): Result<Unit> = runAuthorityCall { api ->
        api.signApproval(
            authorization = bearer(accessToken),
            approvalId = approvalId,
            body = AuthorityApprovalSignRequest(signature = signatureB64Url),
        )
    }

    private fun bearer(accessToken: String) = "Bearer $accessToken"

    private fun AuthorityRecordSubmission.toRequest() = AuthorityRecordSubmissionRequest(
        record = recordB64Url,
        signature = signatureB64Url,
        challenge = challengeB64Url,
        recoveryArtifactConfirmed = recoveryArtifactConfirmed,
    )

    private fun AuthoritySubmissionResponse.toSubmission() = AuthoritySubmission(
        seq = seq,
        state = state,
        effectiveAtEpochSeconds = effectiveAtEpochSeconds,
        recordHash = recordHash,
    )

    private inline fun <T> runAuthorityCall(block: (AccountAuthorityApi) -> T): Result<T> {
        val baseUrl = deployment.identityServiceBaseUrl
            ?: return Result.failure(AuthorityError.NotConfigured)

        val api = try {
            retrofitFactory.create(baseUrl.ensureProtocol()).create(AccountAuthorityApi::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create identity-service Retrofit instance")
            return Result.failure(AuthorityError.Transport(e))
        }

        return try {
            Result.success(block(api))
        } catch (e: HttpException) {
            Result.failure(e.toAuthorityError())
        } catch (e: Exception) {
            // The message only: a body here holds a signature, a challenge or a PIN.
            Timber.w("An account authority call failed: %s", e.javaClass.simpleName)
            Result.failure(AuthorityError.Transport(e))
        }
    }

    /**
     * Maps one refusal onto its typed case. The code is the contract, not the status: the server states the
     * rule that refused a transition, and a client that read only the status could not tell "wait out a
     * backoff" from "this position is not permitted at all".
     */
    private fun HttpException.toAuthorityError(): AuthorityError {
        val rawBody = response()?.errorBody()?.string()
        val body = rawBody?.let { tryOrNull { errorBodyJson.decodeFromString<IdentityServiceErrorBody>(it) } }
        val headerRetryAfter = response()?.headers()?.get("Retry-After")?.toLongOrNull()
        val retryAfter = body?.retryAfterSeconds ?: headerRetryAfter
        return when (val code = body?.code) {
            "authority_disabled" -> AuthorityError.Disabled
            "authority_step_up_required" -> AuthorityError.StepUpRequired
            "authority_factor_too_fresh" -> AuthorityError.FactorTooFresh
            "authority_recovery_too_recent" -> AuthorityError.RecoveryTooRecent
            "authority_artifact_unconfirmed" -> AuthorityError.ArtifactUnconfirmed
            "authority_native_session_required" -> AuthorityError.NativeSessionRequired
            "authority_position_refused",
            "authority_adoption_not_permitted" -> AuthorityError.PositionRefused
            "authority_pending_conflict" -> AuthorityError.PendingConflict
            "authority_head_conflict",
            "authority_account_mismatch" -> AuthorityError.HeadConflict
            "authority_last_device" -> AuthorityError.LastDevice
            "authority_signer_refused",
            "authority_challenge_invalid" -> AuthorityError.SignerRefused
            "authority_device_quarantined" -> AuthorityError.DeviceQuarantined
            "authority_backoff" -> AuthorityError.Backoff(retryAfter)
            "authority_cooldown" -> AuthorityError.Cooldown(retryAfter)
            "authority_no_account" -> AuthorityError.NoAccount
            "authority_approval_invalid" -> AuthorityError.ApprovalInvalid
            "authority_approval_limit" -> AuthorityError.ApprovalLimit
            "authority_opposition_device_required" -> AuthorityError.OppositionDeviceRequired
            "authority_opposition_refused" -> AuthorityError.OppositionRefused
            "invalid_authority_record" -> AuthorityError.InvalidRecord(body.message)
            else -> when {
                // A 503 with no code at all is still this feature being off, which is the state every
                // deployment is in today: treating it as a server error would show an error for the normal
                // case.
                code == null && code() == 503 -> AuthorityError.Disabled
                else -> AuthorityError.Server(code())
            }
        }
    }

    private companion object {
        private val errorBodyJson = Json { ignoreUnknownKeys = true }
    }
}
