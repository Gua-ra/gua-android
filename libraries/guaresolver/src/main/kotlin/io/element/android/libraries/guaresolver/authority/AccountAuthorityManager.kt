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
 * is accepted on the strength of one (ADM-009 decision 9). There is no `revoke` and no `recover`, because
 * this milestone ships no screen for either and a method with no screen behind it would claim a capability
 * the app does not have. And there is no second adoption: an account that has lost its authority may not
 * adopt again, which is decision 7's permitted terminal state rather than a gap.
 */
interface AccountAuthorityManager {
    /** Reads the chain, the device set and any pending transition. */
    suspend fun state(accessToken: String): Result<AuthorityChainState>

    /** True when this device holds an authority key the chain recognises, so it can sign for the account. */
    suspend fun holdsAuthority(): Boolean

    /**
     * Generates this device's authority key and the recovery authority key, and returns the recovery
     * artifact to show exactly once.
     *
     * Generating them is not adopting: nothing has reached the server, and a client that stops here has
     * produced only two keys the next attempt will replace.
     */
    suspend fun beginAdoption(): Result<AdoptionOffer>

    /**
     * Signs and submits the `AdoptRoot`, after the step-up [pin] settles.
     *
     * @param accessToken the caller's own session.
     * @param chain the chain as `GET /account/authority` last reported it, which is where the accountId and
     * the position come from.
     * @param deviceLabel what a notification about this adoption may name, at most 16 bytes of UTF-8.
     * @param pin the step-up factor, or null on an account that holds none.
     * @param artifactConfirmed that the recovery artifact was shown and the holder said they stored it.
     * This is refused here as well as by the server, because adoption is what makes the artifact the only
     * way back and an adoption that skipped it is the one state ADM-009 decision 7 will not let the user
     * reach by accident.
     */
    suspend fun adopt(
        accessToken: String,
        chain: AuthorityChainState,
        deviceLabel: String,
        pin: String?,
        artifactConfirmed: Boolean,
    ): Result<AuthoritySubmission>

    /**
     * Objects to the pending transition. [pin] is null on an account's first opposition, which is
     * deliberately cheap, and carries the step-up on the second and later.
     */
    suspend fun oppose(accessToken: String, recordHash: String?, pin: String?): Result<Unit>

    /**
     * Signs and submits a `DeviceGrant` over a key the new device generated itself.
     *
     * @param accessToken the caller's own session.
     * @param chain the chain as last reported, whose head this grant appends to.
     * @param granteeDeviceKeyB64Url the new device's own authority public key. This client never sends one
     * of its own keys to another device, and never accepts one from it.
     * @param label what a notification about this grant may name.
     * @param pin the step-up factor, or null on an account that holds none.
     */
    suspend fun grantDevice(
        accessToken: String,
        chain: AuthorityChainState,
        granteeDeviceKeyB64Url: String,
        label: String,
        pin: String?,
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
}

/**
 * An adoption that has been prepared on this device and not yet submitted.
 *
 * [recoveryArtifact] is the private recovery authority key, rendered for a person. It is shown once, the
 * user confirms they stored it, and only then may the adoption be submitted.
 */
data class AdoptionOffer(
    val recoveryArtifact: String,
)
