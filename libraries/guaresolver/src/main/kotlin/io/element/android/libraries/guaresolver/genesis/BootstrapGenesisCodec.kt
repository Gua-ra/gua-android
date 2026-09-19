/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * GUA FORK: the canonical codec for `BootstrapGenesis`, suite 0x00 (ADM-008 encoding tables).
 *
 * ```
 * off len field
 * 0   4   magic "GUAB"
 * 4   1   version = 0x01
 * 5   1   suite = 0x00
 * 6   16  entropy   CSPRNG, never derived from the MXID or the phone
 * 22      end
 * ```
 *
 * The fixed-layout rationale is the one [AccountGenesisCodec] documents.
 */
object BootstrapGenesisCodec {
    private val magic = BootstrapGenesis.MAGIC.toByteArray(Charsets.US_ASCII)

    private const val OFFSET_VERSION = 4
    private const val OFFSET_SUITE = 5
    private const val OFFSET_ENTROPY = 6

    /**
     * Strictly decodes canonical bytes.
     *
     * @throws InvalidGenesisException on a wrong length, magic, version or suite.
     */
    fun decode(bytes: ByteArray): BootstrapGenesis {
        if (bytes.size != BootstrapGenesis.LENGTH) {
            throw InvalidGenesisException("wrong_length", "BootstrapGenesis must be exactly ${BootstrapGenesis.LENGTH} bytes")
        }
        if (!bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw InvalidGenesisException("bad_magic", "BootstrapGenesis magic is not ${BootstrapGenesis.MAGIC}")
        }
        val version = bytes[OFFSET_VERSION].toInt() and 0xFF
        if (version != BootstrapGenesis.VERSION) {
            throw InvalidGenesisException("unknown_version", "unknown BootstrapGenesis version")
        }
        val suite = bytes[OFFSET_SUITE].toInt() and 0xFF
        if (suite != BootstrapGenesis.SUITE_NONE) {
            throw InvalidGenesisException("unknown_suite", "unknown BootstrapGenesis suite")
        }
        val entropy = bytes.copyOfRange(OFFSET_ENTROPY, BootstrapGenesis.LENGTH)
        return BootstrapGenesis(version, suite, entropy, bytes)
    }

    /** Builds canonical bytes over the given entropy. */
    fun encode(entropy: ByteArray): ByteArray {
        require(entropy.size == BootstrapGenesis.ENTROPY_LENGTH) { "entropy is ${BootstrapGenesis.ENTROPY_LENGTH} bytes" }
        val out = ByteArray(BootstrapGenesis.LENGTH)
        magic.copyInto(out, 0)
        out[OFFSET_VERSION] = BootstrapGenesis.VERSION.toByte()
        out[OFFSET_SUITE] = BootstrapGenesis.SUITE_NONE.toByte()
        entropy.copyInto(out, OFFSET_ENTROPY)
        return out
    }
}
