/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import java.security.SecureRandom

/**
 * GUA FORK: the canonical codec for `AccountGenesis`, suite 0x01 (ADM-008 encoding tables). Port of the
 * identity-service `AccountGenesisCodec`, and verified against the published golden vectors.
 *
 * ```
 * off len field
 * 0   4   magic "GUAG"
 * 4   1   genesisVersion = 0x01
 * 5   1   suite = 0x01
 * 6   32  authorityPublicKey          raw RFC 8032 Ed25519
 * 38  1   recoveryFrameworkId = 0x01
 * 39  32  recoveryAuthorityPublicKey  raw Ed25519, must differ from the authority key
 * 71  16  entropy                     CSPRNG
 * 87      end
 * ```
 *
 * The layout is fixed rather than length-prefixed because the accountId is a permanent hash of these
 * bytes, so the bytes that are hashed must be the bytes that crossed the wire; a framing with a
 * parse-then-re-serialize step invites exactly the re-encoding this must never do.
 */
object AccountGenesisCodec {
    private val magic = AccountGenesis.MAGIC.toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    private const val OFFSET_VERSION = 4
    private const val OFFSET_SUITE = 5
    private const val OFFSET_AUTHORITY_KEY = 6
    private const val OFFSET_FRAMEWORK = 38
    private const val OFFSET_RECOVERY_KEY = 39
    private const val OFFSET_ENTROPY = 71

    /**
     * Strictly decodes canonical bytes. The returned object keeps the bytes exactly as passed in, so the
     * accountId is derived from what was received.
     *
     * @throws InvalidGenesisException on any rule ADM-008 decision 1 states.
     */
    fun decode(bytes: ByteArray): AccountGenesis {
        if (bytes.size != AccountGenesis.LENGTH) {
            throw InvalidGenesisException("wrong_length", "AccountGenesis must be exactly ${AccountGenesis.LENGTH} bytes")
        }
        if (!bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw InvalidGenesisException("bad_magic", "AccountGenesis magic is not ${AccountGenesis.MAGIC}")
        }
        val version = bytes[OFFSET_VERSION].toInt() and 0xFF
        if (version != AccountGenesis.VERSION) {
            throw InvalidGenesisException("unknown_version", "unknown AccountGenesis version")
        }
        val suite = bytes[OFFSET_SUITE].toInt() and 0xFF
        if (suite != AccountGenesis.SUITE_ED25519_SHA256) {
            throw InvalidGenesisException("unknown_suite", "unknown AccountGenesis suite")
        }
        val frameworkId = bytes[OFFSET_FRAMEWORK].toInt() and 0xFF
        if (frameworkId != AccountGenesis.RECOVERY_FRAMEWORK_COMMITTED_KEY) {
            throw InvalidGenesisException("unknown_recovery_framework", "unknown recovery framework id")
        }

        val authorityKey = bytes.copyOfRange(OFFSET_AUTHORITY_KEY, OFFSET_FRAMEWORK)
        val recoveryKey = bytes.copyOfRange(OFFSET_RECOVERY_KEY, OFFSET_ENTROPY)
        val entropy = bytes.copyOfRange(OFFSET_ENTROPY, AccountGenesis.LENGTH)

        // The all-zero rule is separate from point decoding on purpose: the all-zero encoding decodes to
        // a valid low-order point, so point decoding alone would let it through.
        if (Ed25519PublicKeys.isAllZero(authorityKey)) {
            throw InvalidGenesisException("zero_authority_key", "authority key is all zero")
        }
        if (Ed25519PublicKeys.isAllZero(recoveryKey)) {
            throw InvalidGenesisException("zero_recovery_key", "recovery authority key is all zero")
        }
        if (authorityKey.contentEquals(recoveryKey)) {
            throw InvalidGenesisException("duplicate_keys", "recovery authority key must differ from the authority key")
        }
        Ed25519PublicKeys.require(authorityKey, "invalid_authority_key")
        Ed25519PublicKeys.require(recoveryKey, "invalid_recovery_key")

        return AccountGenesis(version, suite, authorityKey, frameworkId, recoveryKey, entropy, bytes)
    }

    /** Builds canonical bytes. The client encodes the genesis it is about to register, then hashes those bytes. */
    fun encode(
        authorityPublicKey: ByteArray,
        recoveryFrameworkId: Int,
        recoveryAuthorityPublicKey: ByteArray,
        entropy: ByteArray,
    ): ByteArray {
        require(
            authorityPublicKey.size == Ed25519PublicKeys.RAW_PUBLIC_KEY_LENGTH &&
                recoveryAuthorityPublicKey.size == Ed25519PublicKeys.RAW_PUBLIC_KEY_LENGTH
        ) { "Ed25519 public keys are ${Ed25519PublicKeys.RAW_PUBLIC_KEY_LENGTH} bytes" }
        require(entropy.size == AccountGenesis.ENTROPY_LENGTH) { "entropy is ${AccountGenesis.ENTROPY_LENGTH} bytes" }
        val out = ByteArray(AccountGenesis.LENGTH)
        magic.copyInto(out, 0)
        out[OFFSET_VERSION] = AccountGenesis.VERSION.toByte()
        out[OFFSET_SUITE] = AccountGenesis.SUITE_ED25519_SHA256.toByte()
        authorityPublicKey.copyInto(out, OFFSET_AUTHORITY_KEY)
        out[OFFSET_FRAMEWORK] = recoveryFrameworkId.toByte()
        recoveryAuthorityPublicKey.copyInto(out, OFFSET_RECOVERY_KEY)
        entropy.copyInto(out, OFFSET_ENTROPY)
        return out
    }

    /**
     * Mints canonical bytes for a fresh genesis over the two device-generated keys.
     *
     * Every genesis carries 16 bytes of CSPRNG entropy (ADM-008 decision 3), so two devices that somehow
     * generated the same key pair would still get distinct accountIds.
     */
    fun mint(authorityPublicKey: ByteArray, recoveryAuthorityPublicKey: ByteArray): ByteArray {
        val entropy = ByteArray(AccountGenesis.ENTROPY_LENGTH)
        random.nextBytes(entropy)
        return encode(
            authorityPublicKey = authorityPublicKey,
            recoveryFrameworkId = AccountGenesis.RECOVERY_FRAMEWORK_COMMITTED_KEY,
            recoveryAuthorityPublicKey = recoveryAuthorityPublicKey,
            entropy = entropy,
        )
    }
}
