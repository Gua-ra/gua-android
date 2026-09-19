/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * GUA FORK: the device-only account authority key pair ADM-008 decision 5 commits, and the recovery
 * authority key framework 0x01 commits beside it.
 *
 * The keys are generated on device before the OIDC flow starts and never leave it. There is no escrow
 * and no sync: ADM-008's own consequences section states that a lost device loses authority, which is
 * the deliberate cost of the key being unexportable.
 *
 * There are two slots, and the difference between them is the difference between a key that can be
 * thrown away and one that cannot. The SIGNUP slot holds the pair minted for the signup in flight,
 * which owns nothing until the server attaches it, so a later signup may replace it and [clear] may
 * drop it. The ATTACHED slot holds the pair that owns an account: it is what that account's authority
 * IS, and under recovery framework 0x01 the recovery key is sealed beside it, so nothing here ever
 * overwrites or deletes it. [markAttached] is what moves a pair from the first slot to the second.
 */
interface AccountAuthorityKeyStore {
    /** True when a pair for the signup in flight has been generated and is still readable. */
    suspend fun hasKeyPair(): Boolean

    /**
     * Generates a fresh authority and recovery key pair for a signup, replacing any earlier pair in the
     * signup slot, which by definition was never attached to an account.
     *
     * An attached pair is not touched, because replacing it would destroy the authority of the account
     * that already owns it and, with it, the recovery key sealed beside it.
     *
     * @return the two raw 32-byte Ed25519 PUBLIC keys, which are the only halves that ever leave here.
     */
    suspend fun createKeyPair(): AccountAuthorityPublicKeys

    /** The public halves of the pair in the signup slot, or null when none is stored. */
    suspend fun publicKeys(): AccountAuthorityPublicKeys?

    /**
     * Records that the pair in the signup slot now owns [accountId], moving it to the attached slot
     * where no later signup and no [clear] can reach it. Calling it again for the same account does
     * nothing, so an attach step that is retried is safe.
     *
     * @throws IllegalStateException when the signup slot is empty, or when a DIFFERENT account is
     * already attached on this device: promoting over that pair would be the one loss ADM-008 decision
     * 5 offers no recovery from, so it is refused rather than performed.
     */
    suspend fun markAttached(accountId: AccountId)

    /** The accountId owned by the attached pair, or null when no pair on this device is attached. */
    suspend fun attachedAccountId(): String?

    /** The public halves of the attached pair, or null when no pair is attached. */
    suspend fun attachedPublicKeys(): AccountAuthorityPublicKeys?

    /**
     * Signs [message] with the account authority key of the signup in flight.
     *
     * @return the 64-byte detached Ed25519 signature.
     * @throws IllegalStateException when the signup slot is empty, so a caller can never mistake a
     * missing key for a successful signature.
     */
    suspend fun signWithAuthorityKey(message: ByteArray): ByteArray

    /** Forgets the pair in the signup slot. An attached pair is kept. */
    suspend fun clear()

    // GUA FORK: ADM-009. The same store holds the authority CHAIN's keys, because they are the same kind of
    // thing as the genesis pair and must not be a second place a key can be lost: one keystore key seals
    // every slot, and adding a second store would mean a second thing that can be cleared while the account
    // still depends on it.
    //
    // There are two more slots, and the difference is again a key that can be thrown away against one that
    // cannot. The ADOPTION slot holds the pair minted for an adoption in flight, which owns nothing until the
    // chain accepts it. The ADOPTED slot holds the pair that is the account's authority on this device.

    /**
     * Generates this device's authority key and the recovery authority key for an adoption in flight,
     * replacing any earlier pair in the adoption slot, which by definition the chain never accepted.
     *
     * The adopted pair is never touched: replacing it would destroy the authority the chain already
     * recognises on this device, and ADM-009 decision 7 offers no way back from that except the recovery
     * artifact.
     *
     * @return the two raw 32-byte Ed25519 PUBLIC keys, plus the recovery artifact the user must store.
     */
    suspend fun createAdoptionKeys(): AdoptionKeys

    /** The adoption slot's public halves and artifact, or null when no adoption has been started here. */
    suspend fun adoptionKeys(): AdoptionKeys?

    /**
     * Records that the adoption slot's pair is now this account's authority on this device, moving it to the
     * adopted slot. Calling it again for the same account does nothing, so a retried submission is safe.
     *
     * @throws IllegalStateException when the adoption slot is empty, or when a DIFFERENT account is already
     * adopted on this device.
     */
    suspend fun markAdopted(accountId: String)

    /** The accountId whose authority this device holds through the chain, or null. */
    suspend fun adoptedAccountId(): String?

    /**
     * This device's authority public key as the chain knows it: the adopted pair's device key, falling back
     * to the attached genesis authority key.
     *
     * The fallback is not a convenience. On a class 0x01 account the genesis authority key IS the first
     * device key, committed by the accountId rather than by a record (ADM-009 decision 10), and the server
     * materialises it as a device row so every later rule can read the device set uniformly. A signer that
     * knew only about adopted pairs would tell such an account it holds no authority.
     */
    suspend fun authorityDevicePublicKey(): ByteArray?

    /**
     * Signs [message] with the authority key of the adoption in flight, which is the key the `AdoptRoot`
     * it signs commits.
     *
     * @throws IllegalStateException when the adoption slot is empty, so a caller can never mistake a missing
     * key for a successful signature.
     */
    suspend fun signWithAdoptionKey(message: ByteArray): ByteArray

    /**
     * Signs [message] as this account's authority device: with the adopted device key, falling back to the
     * attached genesis authority key for the reason [authorityDevicePublicKey] gives.
     *
     * @throws IllegalStateException when this device holds no authority key at all.
     */
    suspend fun signAsAuthorityDevice(message: ByteArray): ByteArray
}

/**
 * The public halves of an adoption's pair, and the recovery artifact that pair commits.
 *
 * The artifact is the PRIVATE recovery key, encoded for a person (see
 * [io.element.android.libraries.guaresolver.authority.RecoveryArtifact]). It is in this type because
 * ADM-009 decision 7 refuses an adoption that did not show it, so the one call that mints the keys is the
 * one call that can hand it over: nothing else has to ask for a secret, and no screen can reach one for a
 * pair it did not just create.
 */
class AdoptionKeys(
    private val device: ByteArray,
    private val recovery: ByteArray,
    val recoveryArtifact: String,
) {
    fun deviceAuthorityPublicKey(): ByteArray = device.copyOf()
    fun recoveryAuthorityPublicKey(): ByteArray = recovery.copyOf()
}

/**
 * The two raw 32-byte Ed25519 public keys a suite 0x01 genesis commits. Only public halves travel in
 * this type; the private halves never leave [AccountAuthorityKeyStore].
 */
class AccountAuthorityPublicKeys(
    private val authority: ByteArray,
    private val recovery: ByteArray,
) {
    fun authorityPublicKey(): ByteArray = authority.copyOf()
    fun recoveryAuthorityPublicKey(): ByteArray = recovery.copyOf()
}
