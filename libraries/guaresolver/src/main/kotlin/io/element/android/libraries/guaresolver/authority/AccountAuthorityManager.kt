/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

/**
 * GUA FORK: the client half of ADM-009. Generates and holds this device's authority keys, and turns each
 * transition into the exact three steps the wire asks for: a challenge minted with a scoped step-up, a
 * record signed over that challenge, and a submission carrying both.
 *
 * Nothing here runs unless the caller has checked the account-authority feature flag: with the flag off, no
 * screen reaches this type and no request is made, which is what "ships disabled" means on this side.
 *
 * WHAT IS DELIBERATELY NOT HERE. There is no method that takes a phone code, because no record in this chain
 * is accepted on the strength of one (ADM-009 decision 9). And there is no second adoption: an account that
 * has lost its authority may not adopt again, which is decision 7's permitted terminal state rather than a
 * gap, so [adopt] is reachable only from a chain that reports it can.
 */
interface AccountAuthorityManager {
    /** Reads the chain, the device set and any pending transition. */
    suspend fun state(accessToken: String): Result<AuthorityChainState>

    /** True when this device holds an authority key the chain recognises, so it can sign for the account. */
    suspend fun holdsAuthority(): Boolean

    /** This device's own authority public key as the chain would name it, or null when it holds none. */
    suspend fun authorityDeviceKeyB64Url(): String?

    /**
     * Generates this device's authority key and the recovery authority key, and returns the recovery
     * artifact to show exactly once.
     *
     * Generating them is not adopting: nothing has reached the server, and a client that stops here has
     * produced only two keys the next attempt will replace.
     */
    suspend fun beginAdoption(): Result<AdoptionOffer>

    /**
     * Signs and submits the `AdoptRoot`, after [stepUp] settles.
     *
     * @param accessToken the caller's own session.
     * @param chain the chain as `GET /account/authority` last reported it, which is where the accountId and
     * the position come from.
     * @param deviceLabel what a notification about this adoption may name, at most 16 bytes of UTF-8.
     * @param stepUp the factor that authorizes it: a passkey assertion where the account holds one, the PIN
     * otherwise, and never a phone code.
     * @param artifactConfirmed that the recovery artifact was shown and the holder said they stored it.
     * This is refused here as well as by the server, because adoption is what makes the artifact the only
     * way back and an adoption that skipped it is the one state ADM-009 decision 7 will not let the user
     * reach by accident.
     */
    suspend fun adopt(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission>

    /**
     * Mints the one-time URL of the WEB step-up for [purpose], to open in a Custom Tab.
     *
     * This is how the step-up policy is whole on this platform. ADM-009 decision 4 accepts a user-verifying
     * passkey assertion or the PIN, and this app cannot produce an assertion for a bearer session of its own,
     * so the ceremony runs on the page identity-service serves, exactly as first-PIN enrollment already does.
     * What comes back is a URL and nothing else: the proof the page leaves behind is a row the server wrote,
     * which the next [state]-driven transition spends by asking for its challenge with
     * [AuthorityStepUp.WebSheet].
     *
     * [purpose] is the transition it is scoped to, and the scoping is the security property: a proof taken for
     * one purpose is refused for another, here and again on the server. The purposes that ask for no factor
     * ([AuthorityPurpose.OPPOSE], [AuthorityPurpose.APPROVE], [AuthorityPurpose.NOTIFY]) are refused with
     * [AuthorityError.StepUpPurposeRefused] before anything is requested, because a proof recorded for one of
     * them would be a proof of nothing.
     *
     * **No arm of that page sends a code to the account's number** (decision 9), and nothing here can ask it
     * to: the request carries a purpose and at most this build's own redirect.
     */
    suspend fun startWebStepUp(accessToken: String, purpose: AuthorityPurpose): Result<String>

    /**
     * Objects to the pending transition as a SESSION, which decision 4 permits against an adoption and
     * nothing else. [pin] is null on an account's first opposition, which is deliberately cheap, and carries
     * the step-up on the second and later.
     */
    suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit>

    /**
     * Objects to the pending transition as a DEVICE, with a signed `Oppose` record.
     *
     * This is the only objection the server accepts against a grant, a revocation or a recovery. It asks for
     * no factor: the authorization is the signature, and the fresh-factor hold gates starting a transition
     * and never objecting to one, so an owner who has just changed their PIN to lock a thief out is not the
     * one disarmed by it.
     */
    suspend fun opposeWithRecord(accessToken: String, chain: AuthorityChainState): Result<Unit>

    /**
     * Offers THIS device's own key as a candidate for a grant by another device, and returns the fingerprint
     * the person holding that other phone has to see.
     *
     * The public half only. This client never sends one of its own private keys anywhere and never accepts a
     * key from another device.
     */
    suspend fun offerThisDeviceForGrant(accessToken: String, label: String): Result<AuthorityCandidate>

    /** The keys this account's other devices have offered, each with the fingerprint recomputed here. */
    suspend fun candidates(accessToken: String): Result<List<AuthorityCandidate>>

    /**
     * Signs and submits a `DeviceGrant` over a candidate the new device registered itself.
     *
     * `candidate` is the offer as [candidates] reported it, and its fingerprint is recomputed from its key
     * before anything is signed, so a fingerprint chosen by whoever answered the request cannot be the one a
     * person compared.
     *
     * `fingerprintConfirmed` is that the person compared the fingerprint against the other phone's screen and
     * said it matched. The grant is refused without it: the fingerprint is the only thing binding these 32
     * bytes to the human holding the other device, and a grant that skipped the comparison is a grant over
     * whatever key arrived.
     */
    suspend fun grantDevice(
        accessToken: String,
        chain: AuthorityChainState,
        candidate: AuthorityCandidate,
        stepUp: AuthorityStepUp,
        fingerprintConfirmed: Boolean,
    ): Result<AuthoritySubmission>

    /**
     * Signs and submits a `DeviceRevoke`.
     *
     * Revoking another device waits out the window and is notified; revoking this device's own key takes
     * effect at once. The difference is not a parameter: the server reads it from whether the key named is
     * the key that signed, so a client cannot ask for the immediate path by saying it is a self-revocation.
     */
    suspend fun revokeDevice(
        accessToken: String,
        chain: AuthorityChainState,
        deviceKeyB64Url: String,
        reason: Int,
        stepUp: AuthorityStepUp,
    ): Result<AuthoritySubmission>

    /**
     * Prepares an `AuthorityRecovery`: validates the artifact the owner typed back, and mints the pair the
     * record will install.
     *
     * Validated before anything is spent, because the artifact never crosses the wire: what crosses is a
     * signature by it, so malformed material rejected by the server would arrive as
     * `authority_signer_refused` after a challenge and a step-up were already gone.
     *
     * @param recoveryArtifact the artifact as the person typed or pasted it.
     * @return the NEW artifact the recovery commits, to show once and confirm, exactly as adoption does.
     */
    suspend fun beginRecovery(recoveryArtifact: String): Result<AdoptionOffer>

    /**
     * Signs and submits the `AuthorityRecovery` prepared by [beginRecovery], authorized by the committed
     * recovery authority key (authorization 0x01).
     *
     * This is the rank-2 record: no pending transition blocks it, and a thief holding every device cannot
     * cancel it. It is signed by the key in the artifact, which is the only reason that artifact matters.
     */
    suspend fun recoverAuthority(
        accessToken: String,
        chain: AuthorityChainState,
        recoveryArtifact: String,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission>

    /**
     * Prepares an `AuthorityRecovery` authorized through a COMPLETED ACCOUNT RECOVERY (authorization 0x02),
     * for an owner who no longer has the artifact.
     *
     * It mints the pair the record installs and hands back the new artifact, exactly as [beginAdoption] and
     * [beginRecovery] do, and it reads nothing back: there is no old artifact to validate, which is the whole
     * difference between the two routes.
     */
    suspend fun beginAccountRecovery(): Result<AdoptionOffer>

    /**
     * Signs and submits the `AuthorityRecovery` prepared by [beginAccountRecovery], under authorization 0x02.
     *
     * The weaker of the two recovery routes, and the copy on the way in has to say so. It is rank 0: any
     * active device of the account may veto it immediately, a rank-1 or rank-2 record cancels it, and the
     * server refuses it outright on an account whose id commits its authority (class 0x01) and while the
     * account's last completed recovery is inside the fresh-factor hold. What signs it is the device key the
     * record installs, because under 0x02 the record names no authorizing key at all.
     *
     * It exists because the alternative for an owner who lost every device and the artifact is the terminal
     * state of decision 7, and the one thing that keeps this route from being a seizure is that the devices
     * an intruder does not hold can say no to it while it waits.
     */
    suspend fun recoverThroughAccountRecovery(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        stepUp: AuthorityStepUp,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission>

    /**
     * The live approvals a browser session started and only an authority device can grant.
     *
     * An account holds at most three at once and their codes are unique among them, which is what lets a
     * device show one code and mean one action.
     */
    suspend fun approvals(accessToken: String): Result<List<AuthorityApproval>>

    /**
     * Signs one pending browser approval as this account's authority device.
     *
     * No challenge call: the approval carries the challenge the browser's own start minted, and that is the
     * value inside the signature.
     */
    suspend fun approve(
        accessToken: String,
        chain: AuthorityChainState,
        approval: AuthorityApproval,
    ): Result<Unit>

    /**
     * Registers this install as the security-notification destination for this account (ADM-009 gate 2).
     *
     * Not chained to the Matrix pusher, on purpose: a pusher lives under a session and a completed account
     * recovery ends every session of the user, so the pusher dies with the thing the attacker just destroyed.
     * This row is keyed on an installation id sealed on the device and stable across sign-out.
     *
     * When this device holds an authority key the registration is BOUND to it, with a signature over the
     * `gua-authority-notification.v1` preimage. Without that signature the key would be a claim, and an
     * attacker could plant a row naming a key the owner's own device could then never remove.
     */
    suspend fun registerSecurityNotifications(
        accessToken: String,
        pushToken: String,
        platform: String,
        appId: String,
        deviceLabel: String,
    ): Result<Unit>

    /** The registrations this account holds, named but never with their destinations. */
    suspend fun securityNotifications(accessToken: String): Result<List<SecurityNotificationView>>

    /**
     * Removes one registration. Naming this install's own id is the tier that needs no extra factor; naming
     * another install's needs [pin], and the server refuses one inside the fresh-factor hold.
     */
    suspend fun removeSecurityNotification(
        accessToken: String,
        installationId: String,
        pin: String?,
    ): Result<Unit>

    /** This install's own id, as the registration and its removal name it. */
    suspend fun installationId(): String
}

/**
 * An adoption or a recovery that has been prepared on this device and not yet submitted.
 *
 * [recoveryArtifact] is the private recovery authority key, rendered for a person. It is shown once, the
 * user confirms they stored it, and only then may the record be submitted.
 */
data class AdoptionOffer(
    val recoveryArtifact: String,
)
