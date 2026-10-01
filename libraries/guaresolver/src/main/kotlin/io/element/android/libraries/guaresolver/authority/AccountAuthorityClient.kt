/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

interface AccountAuthorityClient {
    suspend fun challenge(
        accessToken: String,
        purpose: AuthorityPurpose,
        stepUp: AuthorityStepUp,
    ): Result<AuthorityChallenge>

    suspend fun startWebStepUp(accessToken: String, purpose: AuthorityPurpose): Result<String>

    suspend fun adopt(accessToken: String, submission: AuthorityRecordSubmission): Result<AuthoritySubmission>

    suspend fun grantDevice(accessToken: String, submission: AuthorityRecordSubmission): Result<AuthoritySubmission>

    suspend fun revokeDevice(accessToken: String, submission: AuthorityRecordSubmission): Result<AuthoritySubmission>

    suspend fun recoverAuthority(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission>

    suspend fun offerCandidate(
        accessToken: String,
        deviceKeyB64Url: String,
        label: String?,
    ): Result<AuthorityCandidate>

    suspend fun candidates(accessToken: String): Result<List<AuthorityCandidate>>

    suspend fun registerSecurityNotification(
        accessToken: String,
        registration: SecurityNotificationRegistration,
    ): Result<Unit>

    suspend fun securityNotifications(accessToken: String): Result<List<SecurityNotificationView>>

    suspend fun removeSecurityNotification(
        accessToken: String,
        removal: SecurityNotificationRemoval,
    ): Result<Unit>

    suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit>

    suspend fun opposeWithRecord(accessToken: String, submission: AuthorityRecordSubmission): Result<Unit>

    suspend fun state(accessToken: String): Result<AuthorityChainState>

    suspend fun liveApprovals(accessToken: String): Result<List<AuthorityApproval>>

    suspend fun signApproval(accessToken: String, approvalId: String, signatureB64Url: String): Result<Unit>
}

enum class AuthorityPurpose {
    ADOPT,
    GRANT,
    REVOKE,
    RECOVER,
    APPROVE,

    OPPOSE,

    NOTIFY,
}

sealed interface AuthorityStepUp {
    data object None : AuthorityStepUp

    data class Pin(val pin: String) : AuthorityStepUp

    data class Passkey(val stepUpId: String, val credentialJson: String) : AuthorityStepUp

    /** A factor proved in the web sheet. Unlike [None], valid only for purposes that require one. */
    data object WebSheet : AuthorityStepUp
}

/** [fingerprint] is recomputed locally from [deviceKeyB64Url], never taken from the server. */
data class AuthorityCandidate(
    val deviceKeyB64Url: String,
    val fingerprint: String,
    val label: String,
    val expiresAtEpochSeconds: Long,
)

data class SecurityNotificationRegistration(
    val installationId: String,
    val platform: String,
    val token: String,
    val appId: String,
    val deviceLabel: String?,
    val authorityDeviceKeyB64Url: String? = null,
    val challengeB64Url: String? = null,
    val signatureB64Url: String? = null,
) {
    companion object {
        const val PLATFORM_FCM = "FCM"
    }
}

data class SecurityNotificationView(
    val installationId: String,
    val platform: String,
    val deviceLabel: String?,
    val tokenFingerprint: String,
    val boundToAnAuthorityDevice: Boolean,
    val lastSeenAtEpochSeconds: Long,
)

data class SecurityNotificationRemoval(
    val installationId: String,
    val pin: String? = null,
    val challengeB64Url: String? = null,
    val signatureB64Url: String? = null,
)

/** Carries the challenge back: the server stores only its SHA-256 and cannot rebuild the preimage without it. */
data class AuthorityRecordSubmission(
    val recordB64Url: String,
    val signatureB64Url: String,
    val challengeB64Url: String,
    val recoveryArtifactConfirmed: Boolean = false,
)

data class AuthorityChallenge(
    val challengeB64Url: String,
    val expiresInSeconds: Long,
)

data class AuthoritySubmission(
    val seq: Long,
    val state: String,
    val effectiveAtEpochSeconds: Long,
    val recordHash: String,
)

data class AuthorityChainState(
    val accountId: String,
    val accountClass: String,
    val state: String,
    val headSeq: Long,
    val headHash: String,
    val devices: List<AuthorityDevice>,
    val pending: AuthorityPendingTransition?,
) {
    val canAdopt: Boolean = state == STATE_BOOTSTRAP && pending == null && headSeq == 0L

    val isAdoptionPending: Boolean = state == STATE_ADOPTION_PENDING

    companion object {
        const val STATE_BOOTSTRAP = "BOOTSTRAP"
        const val STATE_ADOPTION_PENDING = "ADOPTION_PENDING"
        const val STATE_ROOTED = "ROOTED"
        const val STATE_RECOVERY_PENDING = "RECOVERY_PENDING"
        const val STATE_AUTHORITY_LOST = "AUTHORITY_LOST"
    }
}

data class AuthorityDevice(
    val deviceKeyB64Url: String,
    val label: String,
    val state: String,
    val quarantineUntilEpochSeconds: Long?,
    val grantedSeq: Long,
) {
    val isActive: Boolean = state == "ACTIVE"

    val isQuarantined: Boolean = state == "QUARANTINED"

    val isRevoked: Boolean = state == "REVOKED"
}

data class AuthorityPendingTransition(
    val type: String,
    val seq: Long,
    val effectiveAtEpochSeconds: Long,
    val recordHash: String,
    /**
     * Hash of the record before this one, which an `Oppose` must name. Not the chain's `headHash`: while this
     * record is pending, the head is this record's own hash.
     */
    val prevHash: String? = null,
) {
    val needsADeviceToOppose: Boolean = type != TYPE_ADOPT_ROOT

    companion object {
        const val TYPE_ADOPT_ROOT = "ADOPT_ROOT"
    }
}

data class AuthorityApproval(
    val approvalId: String,
    val code: String,
    val action: String?,
    val actionDigestB64Url: String,
    val challengeB64Url: String,
    val expiresAtEpochSeconds: Long,
)

sealed class AuthorityError(val code: String) : Exception(code) {
    data object Disabled : AuthorityError("authority_disabled")

    data object StepUpRequired : AuthorityError("authority_step_up_required")

    data object StepUpUnavailable : AuthorityError("authority_step_up_unavailable")

    data object StepUpPurposeRefused : AuthorityError("authority_step_up_purpose_refused")

    data object RedirectRefused : AuthorityError("invalid_redirect_uri")

    data object FactorTooFresh : AuthorityError("authority_factor_too_fresh")

    data object RecoveryTooRecent : AuthorityError("authority_recovery_too_recent")

    data object ArtifactUnconfirmed : AuthorityError("authority_artifact_unconfirmed")

    data object NativeSessionRequired : AuthorityError("authority_native_session_required")

    data object PositionRefused : AuthorityError("authority_position_refused")

    data object PendingConflict : AuthorityError("authority_pending_conflict")

    data object HeadConflict : AuthorityError("authority_head_conflict")

    data object LastDevice : AuthorityError("authority_last_device")

    data object SignerRefused : AuthorityError("authority_signer_refused")

    data object DeviceQuarantined : AuthorityError("authority_device_quarantined")

    data object UnknownCandidate : AuthorityError("authority_unknown_candidate")

    data object NoNotificationChannel : AuthorityError("authority_no_notification_channel")

    data object OppositionStale : AuthorityError("authority_opposition_stale")

    data object NotificationsDisabled : AuthorityError("authority_notifications_disabled")

    data object NotificationUnknown : AuthorityError("authority_notification_unknown")

    data class Backoff(val retryAfterSeconds: Long?) : AuthorityError("authority_backoff")

    data class Cooldown(val retryAfterSeconds: Long?) : AuthorityError("authority_cooldown")

    data object NoAccount : AuthorityError("authority_no_account")

    data object ApprovalInvalid : AuthorityError("authority_approval_invalid")

    data object ApprovalLimit : AuthorityError("authority_approval_limit")

    data object OppositionDeviceRequired : AuthorityError("authority_opposition_device_required")

    data object OppositionRefused : AuthorityError("authority_opposition_refused")

    data class InvalidRecord(val reason: String?) : AuthorityError("invalid_authority_record")

    data object NotConfigured : AuthorityError("authority_not_configured")

    data class Transport(override val cause: Throwable?) : AuthorityError("authority_transport")

    data class Server(val status: Int) : AuthorityError("authority_server_error")
}
