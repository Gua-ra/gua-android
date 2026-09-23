/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

/**
 * GUA FORK: the identity-service `/account/authority` endpoints (ADM-009).
 *
 * A separate client from [io.element.android.libraries.guaresolver.IdentityServiceClient] on purpose. Every
 * call here is refused with [AuthorityError.Disabled] until a deployment turns the feature on, and none of
 * them may be reachable from a login, enrollment or recovery path; keeping them behind their own type means
 * no existing caller gains a method it could reach by accident, and a fake for one feature does not have to
 * answer for the other.
 *
 * **There is no phone code in this interface, in any combination, at any step** (ADM-009 decision 9). A
 * method that took one would be the laundering path the record exists to close.
 */
interface AccountAuthorityClient {
    /**
     * Mints the challenge one transition will sign, spending the step-up in the same call
     * (`POST /account/authority/challenge`).
     *
     * The step-up travels with the request rather than being exchanged for a token first, which is how
     * decision 4's "a step-up no older than the challenge" holds by construction: there is no step-up
     * artifact that outlives the challenge.
     *
     * @param accessToken the caller's own session, which the challenge is held against.
     * @param purpose what the challenge may be spent on; one minted for another purpose is refused.
     * @param stepUp the factor that settles it. [AuthorityStepUp.Passkey] is the strong one and the server
     * ranks it first; [AuthorityStepUp.Pin] is the fallback; [AuthorityStepUp.None] is for the purposes that
     * ask for no factor at all, which is an `Oppose` and a first opposition. There is no case for a phone
     * code and no case for saying a factor is unavailable, because a claim like that costs an attacker
     * nothing.
     */
    suspend fun challenge(
        accessToken: String,
        purpose: AuthorityPurpose,
        stepUp: AuthorityStepUp,
    ): Result<AuthorityChallenge>

    /** Submits the signed `AdoptRoot` (`POST /account/authority/adopt`). */
    suspend fun adopt(accessToken: String, submission: AuthorityRecordSubmission): Result<AuthoritySubmission>

    /** Submits the signed `DeviceGrant` (`POST /account/authority/device/grant`). */
    suspend fun grantDevice(accessToken: String, submission: AuthorityRecordSubmission): Result<AuthoritySubmission>

    /** Submits the signed `DeviceRevoke` (`POST /account/authority/device/revoke`). */
    suspend fun revokeDevice(accessToken: String, submission: AuthorityRecordSubmission): Result<AuthoritySubmission>

    /** Submits the signed `AuthorityRecovery` (`POST /account/authority/recover`). */
    suspend fun recoverAuthority(
        accessToken: String,
        submission: AuthorityRecordSubmission,
    ): Result<AuthoritySubmission>

    /**
     * Offers this device's own public key for a grant (`POST /account/authority/device/candidate`).
     *
     * The public half only, under this device's own session. A grant over a key that is not a live candidate
     * of the same account is refused, so this call is what makes the grant nameable at all.
     */
    suspend fun offerCandidate(
        accessToken: String,
        deviceKeyB64Url: String,
        label: String?,
    ): Result<AuthorityCandidate>

    /** The keys this account's new devices have offered (`GET /account/authority/device/candidate`). */
    suspend fun candidates(accessToken: String): Result<List<AuthorityCandidate>>

    /**
     * Registers this install as a security-notification destination
     * (`POST /account/security-notifications`).
     *
     * This is the channel every window in ADM-009 depends on: neither the phone number, which a SIM-swap
     * attacker holds, nor a session, which a completed account recovery revokes in the same transaction that
     * mints the attacker's factor.
     */
    suspend fun registerSecurityNotification(
        accessToken: String,
        registration: SecurityNotificationRegistration,
    ): Result<Unit>

    /** The registrations this account holds, without their destinations (`GET /account/security-notifications`). */
    suspend fun securityNotifications(accessToken: String): Result<List<SecurityNotificationView>>

    /** Removes one registration (`POST /account/security-notifications/remove`). */
    suspend fun removeSecurityNotification(
        accessToken: String,
        removal: SecurityNotificationRemoval,
    ): Result<Unit>

    /**
     * Objects to the transition holding a slot on this account (`POST /account/authority/oppose`). The first
     * opposition needs no factor beyond the session; the second and later need a step-up on any factor at
     * any age, which is why [pin] is here and is null on the first.
     */
    suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit>

    /**
     * Objects with a signed `Oppose` record (`POST /account/authority/oppose/record`).
     *
     * This is the objection an active device makes, and the only one the server accepts against a grant, a
     * revocation or a recovery: a session's word there would let a stolen bearer token veto the owner's own
     * revocation of the thief's device.
     */
    suspend fun opposeWithRecord(accessToken: String, submission: AuthorityRecordSubmission): Result<Unit>

    /** Reads the chain, the device set and any pending transition (`GET /account/authority`). */
    suspend fun state(accessToken: String): Result<AuthorityChainState>

    /** The live approvals waiting for an authority device (`GET /account/authority/approval`). */
    suspend fun liveApprovals(accessToken: String): Result<List<AuthorityApproval>>

    /** Signs one pending approval (`POST /account/authority/approval/{id}/sign`). */
    suspend fun signApproval(accessToken: String, approvalId: String, signatureB64Url: String): Result<Unit>
}

/** What a challenge may be spent on. The server refuses a challenge minted for another purpose. */
enum class AuthorityPurpose {
    ADOPT,
    GRANT,
    REVOKE,
    RECOVER,
    APPROVE,

    /**
     * A signed `Oppose` record. It asks for no factor: the authorization is a signature by a key the chain
     * has active, and the fresh-factor hold gates starting a transition and never objecting to one.
     */
    OPPOSE,

    /** Binding a security-notification registration to a device key, or removing one that carries a binding. */
    NOTIFY,
}

/**
 * The factor that settles one transition's step-up (ADM-009 decision 4 step 2).
 *
 * A sealed type rather than three nullable parameters, so no call site can send a PIN where it meant to send
 * nothing, and so "there is no phone-code case" is a fact about the type rather than a convention.
 */
sealed interface AuthorityStepUp {
    /** No factor. Only for the purposes that ask for none. */
    data object None : AuthorityStepUp

    /** The account PIN, which is the fallback factor. */
    data class Pin(val pin: String) : AuthorityStepUp

    /**
     * A user-verifying passkey assertion, which the server ranks above the PIN and which settles the step-up
     * on its own.
     *
     * [credentialJson] is the assertion exactly as the platform produced it, passed through rather than
     * reshaped: the server verifies what the authenticator signed.
     */
    data class Passkey(val stepUpId: String, val credentialJson: String) : AuthorityStepUp
}

/**
 * A key a new device has offered for a grant, with the fingerprint both devices compute from it.
 *
 * [fingerprint] is what the person holding the granting phone compares against the other phone's screen, and
 * this client recomputes it from [deviceKeyB64Url] rather than showing the one the server sent: a fingerprint
 * taken on trust is a fingerprint an attacker can choose.
 */
data class AuthorityCandidate(
    val deviceKeyB64Url: String,
    val fingerprint: String,
    val label: String,
    val expiresAtEpochSeconds: Long,
)

/**
 * One install registering as a security-notification destination.
 *
 * [installationId] is sealed on the device and stable across sign-out, which is the property that matters:
 * the row has to outlive the sessions a completed account recovery ends.
 *
 * [authorityDeviceKeyB64Url] with [challengeB64Url] and [signatureB64Url] is the binding of ADM-009 gate 2's
 * removal tiers. Without the signature the field would be a claim, and an attacker could plant a row naming a
 * key the owner's own device would then be unable to remove.
 */
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

/** One registration as its own account holder may see it: named, never with its destination. */
data class SecurityNotificationView(
    val installationId: String,
    val platform: String,
    val deviceLabel: String?,
    val tokenFingerprint: String,
    val boundToAnAuthorityDevice: Boolean,
    val lastSeenAtEpochSeconds: Long,
)

/**
 * A removal. The tier is decided by what the caller can produce, never by a field it sets: naming this
 * install's own id in both places is the tier that needs nothing else.
 */
data class SecurityNotificationRemoval(
    val installationId: String,
    val callerInstallationId: String?,
    val pin: String? = null,
    val challengeB64Url: String? = null,
    val signatureB64Url: String? = null,
)

/**
 * One signed record, with the challenge inside its signature.
 *
 * The challenge travels back because the server stores only its SHA-256 and cannot rebuild the preimage
 * without it. That is the wire's deliberate departure from the original sketch, and it costs this client
 * nothing: it already holds the bytes it signed.
 */
data class AuthorityRecordSubmission(
    val recordB64Url: String,
    val signatureB64Url: String,
    val challengeB64Url: String,
    /** Adoption only: that the recovery artifact was shown and the holder confirmed storing it. */
    val recoveryArtifactConfirmed: Boolean = false,
)

data class AuthorityChallenge(
    val challengeB64Url: String,
    val expiresInSeconds: Long,
)

data class AuthoritySubmission(
    val seq: Long,
    /** PENDING while its window runs, ACTIVE when it took effect on acceptance. */
    val state: String,
    val effectiveAtEpochSeconds: Long,
    val recordHash: String,
)

/**
 * The account's chain as its own holder sees it.
 *
 * This is the one response in the service that carries an accountId, and only ever to the account holder,
 * because the client signs over its 34 raw bytes.
 */
data class AuthorityChainState(
    val accountId: String,
    /** BOOTSTRAP or GENESIS: how the id was derived, never whether the account holds authority today. */
    val accountClass: String,
    /** BOOTSTRAP, ADOPTION_PENDING, ROOTED, RECOVERY_PENDING or AUTHORITY_LOST. */
    val state: String,
    val headSeq: Long,
    /** SHA-256 hex of the last accepted record; 64 zeros while the chain is empty. */
    val headHash: String,
    val devices: List<AuthorityDevice>,
    val pending: AuthorityPendingTransition?,
) {
    /** True while an `AdoptRoot` is the transition this account has never had. */
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
    /** Raw 32-byte Ed25519 device authority key, base64url. */
    val deviceKeyB64Url: String,
    val label: String,
    /** ACTIVE, QUARANTINED or REVOKED. */
    val state: String,
    val quarantineUntilEpochSeconds: Long?,
    val grantedSeq: Long,
) {
    val isActive: Boolean = state == "ACTIVE"

    /**
     * A quarantined device can do nothing and counts for nothing, which is what closes the borrowed-phone
     * attack, so a list that drew it as just another device would be lying about what the account holds.
     */
    val isQuarantined: Boolean = state == "QUARANTINED"

    val isRevoked: Boolean = state == "REVOKED"
}

data class AuthorityPendingTransition(
    /** ADOPT_ROOT, DEVICE_GRANT, DEVICE_REVOKE or AUTHORITY_RECOVERY. */
    val type: String,
    val seq: Long,
    val effectiveAtEpochSeconds: Long,
    val recordHash: String,
)

/** A live approval a browser session started and only an authority device can grant. */
data class AuthorityApproval(
    val approvalId: String,
    /** Four characters from an alphabet with no look-alikes, shown on both screens. */
    val code: String,
    /** The opaque action id the device turns into words of its own. */
    val action: String?,
    val actionDigestB64Url: String,
    val challengeB64Url: String,
    val expiresAtEpochSeconds: Long,
)

/**
 * Why an authority call was refused, in the server's own stable vocabulary.
 *
 * Typed rather than a status code, because most of these have a different thing for the user to do and two
 * of them ([Disabled] and [NativeSessionRequired]) are not failures a user can act on at all.
 */
sealed class AuthorityError(val code: String) : Exception(code) {
    /** The deployment does not have this feature on. Not an error worth showing anyone. */
    data object Disabled : AuthorityError("authority_disabled")

    /** No accepted factor was produced. The way out is the account's own passkey or PIN, never an SMS. */
    data object StepUpRequired : AuthorityError("authority_step_up_required")

    /** The presented factor is itself inside the fresh-factor hold identity-service enforces. */
    data object FactorTooFresh : AuthorityError("authority_factor_too_fresh")

    /** The account's last completed recovery is inside that hold, so no authority may be granted yet. */
    data object RecoveryTooRecent : AuthorityError("authority_recovery_too_recent")

    /** The submission did not carry the confirmation that the recovery artifact was stored. */
    data object ArtifactUnconfirmed : AuthorityError("authority_artifact_unconfirmed")

    /** A web session tried to act. There is no flag that lets one sign an authority record. */
    data object NativeSessionRequired : AuthorityError("authority_native_session_required")

    /** This record type is not permitted at this position or on this class of account. */
    data object PositionRefused : AuthorityError("authority_position_refused")

    /** An equal or higher rank record is already pending on this account. */
    data object PendingConflict : AuthorityError("authority_pending_conflict")

    /** The head moved under this submission. Re-read the chain and decide again with the winner in view. */
    data object HeadConflict : AuthorityError("authority_head_conflict")

    /** Accepting it would leave the account with no unquarantined active device. */
    data object LastDevice : AuthorityError("authority_last_device")

    /** The signing key is not one this account's chain accepts at this position. */
    data object SignerRefused : AuthorityError("authority_signer_refused")

    /** This device's own grant is still inside its window, so it may not sign for others yet. */
    data object DeviceQuarantined : AuthorityError("authority_device_quarantined")

    /** A grant named a key that is not a live candidate of this account, so there is nothing to grant. */
    data object UnknownCandidate : AuthorityError("authority_unknown_candidate")

    /**
     * The account holds no live security-notification registration, so a window would run unwitnessed.
     *
     * A legitimate owner can reach this: an install whose registration was removed or whose token rotated
     * away has to register again before it can start a windowed transition. That is the intended direction,
     * and the copy beside it has to say what to do about it rather than reading as a server fault.
     */
    data object NoNotificationChannel : AuthorityError("authority_no_notification_channel")

    /** The objection named a step that is not the one pending. Read the chain again. */
    data object OppositionStale : AuthorityError("authority_opposition_stale")

    /** The deployment has the chain on and this channel off, which is its own separate flag. */
    data object NotificationsDisabled : AuthorityError("authority_notifications_disabled")

    /** No registration with that installation id, or none this account may remove. */
    data object NotificationUnknown : AuthorityError("authority_notification_unknown")

    /** The doubling backoff of ADM-002 D2, paid by the key set that opened a cancelled transition. */
    data class Backoff(val retryAfterSeconds: Long?) : AuthorityError("authority_backoff")

    /** One window must pass before the same account may open another transition of this kind. */
    data class Cooldown(val retryAfterSeconds: Long?) : AuthorityError("authority_cooldown")

    /** The account has no genesis row, so there is no chain to read. */
    data object NoAccount : AuthorityError("authority_no_account")

    /** The approval is gone, expired, or its signature did not verify. Burned either way. */
    data object ApprovalInvalid : AuthorityError("authority_approval_invalid")

    /** Three approvals are already live on this account. */
    data object ApprovalLimit : AuthorityError("authority_approval_limit")

    /** Objecting to this transition has to come from a device that holds the account's authority. */
    data object OppositionDeviceRequired : AuthorityError("authority_opposition_device_required")

    /** This session may not object to this transition. */
    data object OppositionRefused : AuthorityError("authority_opposition_refused")

    /** The bytes were refused by the server's decoder, naming the rule that refused them. */
    data class InvalidRecord(val reason: String?) : AuthorityError("invalid_authority_record")

    /** No identity-service is configured for this build. */
    data object NotConfigured : AuthorityError("authority_not_configured")

    data class Transport(override val cause: Throwable?) : AuthorityError("authority_transport")

    data class Server(val status: Int) : AuthorityError("authority_server_error")
}
