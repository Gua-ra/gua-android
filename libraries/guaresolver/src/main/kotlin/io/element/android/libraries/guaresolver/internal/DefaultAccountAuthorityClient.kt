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
import io.element.android.libraries.guaresolver.EnrollmentRedirectProvider
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.authority.AccountAuthorityClient
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.guaresolver.authority.AuthorityChainState
import io.element.android.libraries.guaresolver.authority.AuthorityChallenge
import io.element.android.libraries.guaresolver.authority.AuthorityDevice
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityFingerprint
import io.element.android.libraries.guaresolver.authority.AuthorityPendingTransition
import io.element.android.libraries.guaresolver.authority.AuthorityPurpose
import io.element.android.libraries.guaresolver.authority.AuthorityRecordSubmission
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.guaresolver.authority.AuthoritySubmission
import io.element.android.libraries.guaresolver.authority.SecurityNotificationRegistration
import io.element.android.libraries.guaresolver.authority.SecurityNotificationRemoval
import io.element.android.libraries.guaresolver.authority.SecurityNotificationView
import io.element.android.libraries.guaresolver.genesis.Base64Url
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
    private val enrollmentRedirectProvider: EnrollmentRedirectProvider,
    private val deployment: GuaDeployment = GuaResolverConfig.current,
) : AccountAuthorityClient {
    override suspend fun challenge(
        accessToken: String,
        purpose: AuthorityPurpose,
        stepUp: AuthorityStepUp,
    ): Result<AuthorityChallenge> = runAuthorityCall { api ->
        val response = api.challenge(
            authorization = bearer(accessToken),
            body = stepUp.toRequest(purpose),
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

    override suspend fun revokeDevice(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission> = runAuthorityCall { api ->
        api.revokeDevice(authorization = bearer(accessToken), body = submission.toRequest()).toSubmission()
    }

    override suspend fun recoverAuthority(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission> = runAuthorityCall { api ->
        api.recoverAuthority(authorization = bearer(accessToken), body = submission.toRequest()).toSubmission()
    }

    override suspend fun offerCandidate(
        accessToken: String,
        deviceKeyB64Url: String,
        label: String?,
    ): Result<AuthorityCandidate> = runAuthorityCall { api ->
        api.offerCandidate(
            authorization = bearer(accessToken),
            body = AuthorityCandidateRequest(deviceKeyB64 = deviceKeyB64Url, label = label),
        ).toCandidate()
    }

    override suspend fun candidates(accessToken: String): Result<List<AuthorityCandidate>> =
        runAuthorityCall { api ->
            api.candidates(authorization = bearer(accessToken)).map { it.toCandidate() }
        }

    override suspend fun registerSecurityNotification(
        accessToken: String,
        registration: SecurityNotificationRegistration,
    ): Result<Unit> = runAuthorityCall { api ->
        // The response names the row and its token fingerprint; the caller registers rather than asks, so it
        // is read for its status and dropped.
        api.registerSecurityNotification(
            authorization = bearer(accessToken),
            body = SecurityNotificationRegisterRequest(
                installationId = registration.installationId,
                platform = registration.platform,
                token = registration.token,
                appId = registration.appId,
                deviceLabel = registration.deviceLabel,
                authorityDeviceKeyB64 = registration.authorityDeviceKeyB64Url,
                challenge = registration.challengeB64Url,
                signature = registration.signatureB64Url,
            ),
        )
    }

    override suspend fun securityNotifications(accessToken: String): Result<List<SecurityNotificationView>> =
        runAuthorityCall { api ->
            api.securityNotifications(authorization = bearer(accessToken)).map { response ->
                SecurityNotificationView(
                    installationId = response.installationId,
                    platform = response.platform,
                    deviceLabel = response.deviceLabel,
                    tokenFingerprint = response.tokenFingerprint,
                    boundToAnAuthorityDevice = response.boundToAnAuthorityDevice,
                    lastSeenAtEpochSeconds = response.lastSeenAtEpochSeconds,
                )
            }
        }

    override suspend fun removeSecurityNotification(
        accessToken: String,
        removal: SecurityNotificationRemoval,
    ): Result<Unit> = runAuthorityCall { api ->
        api.removeSecurityNotification(
            authorization = bearer(accessToken),
            body = SecurityNotificationRemoveRequest(
                installationId = removal.installationId,
                pin = removal.pin?.takeIf { it.isNotEmpty() },
                challenge = removal.challengeB64Url,
                signature = removal.signatureB64Url,
            ),
        )
    }

    override suspend fun opposeWithRecord(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<Unit> = runAuthorityCall { api ->
        api.opposeWithRecord(authorization = bearer(accessToken), body = submission.toRequest())
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

    /**
     * Starts the web step-up, naming this build's own redirect so the sheet closes back into the app it was
     * opened from rather than into whichever variant the deployment happens to default to.
     *
     * The named value is only a request, exactly as it is on a factor-enrollment start: a deployment that has
     * not allowlisted this variant refuses the whole call with 400 `invalid_redirect_uri`, and that refusal is
     * answered once by asking again with no redirect at all. The retry runs at most once, so a second refusal
     * reaches the caller instead of looping.
     */
    override suspend fun startWebStepUp(
        accessToken: String,
        purpose: AuthorityPurpose,
    ): Result<String> {
        suspend fun start(redirectUri: String?): Result<String> = runAuthorityCall { api ->
            api.startWebStepUp(
                authorization = bearer(accessToken),
                body = AuthorityStepUpStartRequest(purpose = purpose.name, redirectUri = redirectUri),
            ).stepUpUrl
        }

        val redirectUri = enrollmentRedirectProvider.provide()?.takeIf { it.isNotBlank() }
            ?: return start(null)
        val named = start(redirectUri)
        if (named.exceptionOrNull() !is AuthorityError.RedirectRefused) return named
        // Never the value itself: it names the build, and the server does not echo it back either.
        Timber.w("The identity service refused this build's step-up redirect, starting again without one")
        return start(null)
    }

    /**
     * The one place a step-up becomes wire fields, written as an exhaustive `when` rather than three casts.
     *
     * Three of the four cases send no factor field at all, and they mean different things: a purpose that asks
     * for nothing, and a factor already proved in the web sheet, which the server looks up against its own row
     * for this account, this token and this purpose. **There is no fifth case and no phone-code field**
     * (ADM-009 decision 9), and an exhaustive `when` is what makes a future case have to say which it is
     * instead of quietly arriving as "no factor".
     */
    private fun AuthorityStepUp.toRequest(purpose: AuthorityPurpose): AuthorityChallengeRequest =
        when (this) {
            AuthorityStepUp.None, AuthorityStepUp.WebSheet -> AuthorityChallengeRequest(purpose = purpose.name)
            is AuthorityStepUp.Pin -> AuthorityChallengeRequest(
                purpose = purpose.name,
                pin = pin.takeIf { it.isNotEmpty() },
            )
            is AuthorityStepUp.Passkey -> AuthorityChallengeRequest(
                purpose = purpose.name,
                passkeyStepUpId = stepUpId,
                // Parsed rather than forwarded as a string, because the field is a JSON object on the wire and
                // a client that sent it quoted would have the server refuse an assertion that was fine.
                passkeyCredential = requestBodyJson.parseToJsonElement(credentialJson),
            )
        }

    private fun bearer(accessToken: String) = "Bearer $accessToken"

    private fun AuthorityRecordSubmission.toRequest() = AuthorityRecordSubmissionRequest(
        record = recordB64Url,
        signature = signatureB64Url,
        challenge = challengeB64Url,
        recoveryArtifactConfirmed = recoveryArtifactConfirmed,
    )

    /**
     * The fingerprint is recomputed from the key rather than read from the response.
     *
     * The comparison a person makes is only worth making if both phones derived it from the same 32 bytes; a
     * string this client simply displayed would let whoever answered the request choose what the user compares.
     * A key that does not decode gets no fingerprint at all, and the screen shows the candidate as unusable
     * rather than showing eight characters of nothing.
     */
    private fun AuthorityCandidateResponse.toCandidate() = AuthorityCandidate(
        deviceKeyB64Url = deviceKeyB64,
        fingerprint = tryOrNull { AuthorityFingerprint.of(Base64Url.decode(deviceKeyB64)) }.orEmpty(),
        label = label.orEmpty(),
        expiresAtEpochSeconds = expiresAtEpochSeconds,
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
            "authority_step_up_unavailable" -> AuthorityError.StepUpUnavailable
            "authority_step_up_purpose_refused" -> AuthorityError.StepUpPurposeRefused
            // Named so `startWebStepUp` can tell it apart from every other 400 and start again with no
            // redirect. Left as a bare server error it would dead-end the one flow it exists to keep open.
            "invalid_redirect_uri" -> AuthorityError.RedirectRefused
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
            "authority_unknown_candidate" -> AuthorityError.UnknownCandidate
            "authority_no_notification_channel" -> AuthorityError.NoNotificationChannel
            "authority_opposition_stale" -> AuthorityError.OppositionStale
            "authority_notifications_disabled" -> AuthorityError.NotificationsDisabled
            "authority_notification_unknown",
            "authority_notification_unknown_device" -> AuthorityError.NotificationUnknown
            "authority_notification_invalid",
            "authority_notification_invalid_key",
            "authority_notification_invalid_signature" -> AuthorityError.InvalidRecord(body.message)
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

        /** Only ever used to reparse an assertion the platform produced, never to build one. */
        private val requestBodyJson = Json { ignoreUnknownKeys = true }
    }
}
