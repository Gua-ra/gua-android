/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import java.security.MessageDigest

/**
 * GUA FORK: an accountId, `"ga1" || base32(0x01 || class || SHA-256(canonical bytes))` (ADM-008
 * decision 2). Port of the identity-service `AccountId`.
 *
 * The digest covers the canonical bytes AS RECEIVED. Nothing here re-encodes a decoded object before
 * hashing it, because the accountId is permanent: if the bytes that were hashed were not the bytes that
 * crossed the wire, a decoder bug would silently mint a different account.
 *
 * An accountId also has exactly one spelling. The 34 input bytes are 272 bits while the 55 base32
 * characters carry 275, so the last character holds three unused bits and only `a`, `i`, `q` and `y` can
 * end a well-formed id. A decoder that ignored that would accept eight spellings of one account, which
 * ADM-001 L4 forbids. [parse] therefore applies the tightened pattern, decodes, re-encodes and compares.
 */
class AccountId private constructor(
    val value: String,
    private val raw: ByteArray,
) {
    /** The 34 bytes under the base32, which a placement record carries verbatim. */
    fun rawBytes(): ByteArray = raw.copyOf()

    /** [CLASS_GENESIS] or [CLASS_BOOTSTRAP]. */
    val rootClass: Byte get() = raw[1]

    /** True for a genesis-rooted account, false for a bootstrap one (ADM-001 L5's audit marker). */
    val isGenesisRooted: Boolean get() = rootClass == CLASS_GENESIS

    override fun equals(other: Any?): Boolean = other is AccountId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        const val PREFIX = "ga1"

        /** accountId format version, the first byte under the base32. */
        const val FORMAT_VERSION: Byte = 0x01

        /** Root class byte: the account is rooted in an `AccountGenesis`. */
        const val CLASS_GENESIS: Byte = 0x01

        /** Root class byte: the account is a bootstrap account (ADM-001 L5 path B1). */
        const val CLASS_BOOTSTRAP: Byte = 0x00

        /** Bytes under the base32: format version, root class, then the 32-byte digest. */
        const val RAW_LENGTH = 34

        /** Characters of base32 that [RAW_LENGTH] bytes produce. */
        const val ENCODED_LENGTH = 55

        /** Total characters, the `ga1` prefix included. */
        const val LENGTH = 58

        /** The canonical spelling, tightened at the last character. */
        const val CANONICAL_PATTERN = "^ga1[a-z2-7]{54}[aiqy]$"

        private val canonical = Regex(CANONICAL_PATTERN)

        /**
         * Derives the accountId of an object from the exact bytes received for it.
         *
         * @param rootClass [CLASS_GENESIS] or [CLASS_BOOTSTRAP].
         * @param canonicalBytes the canonical bytes as received, never a re-encoding of a parsed object.
         */
        fun derive(rootClass: Byte, canonicalBytes: ByteArray): AccountId {
            requireKnownClass(rootClass)
            val raw = ByteArray(RAW_LENGTH)
            raw[0] = FORMAT_VERSION
            raw[1] = rootClass
            MessageDigest.getInstance("SHA-256").digest(canonicalBytes).copyInto(raw, 2)
            return AccountId(PREFIX + Base32.encode(raw), raw)
        }

        /**
         * Parses an accountId, enforcing the canonical form.
         *
         * @throws InvalidGenesisException when the value is not the one canonical spelling of a known
         * format version and root class.
         */
        fun parse(value: String): AccountId {
            if (!canonical.matches(value)) {
                throw InvalidGenesisException("bad_account_id", "accountId does not match the canonical pattern")
            }
            val encoded = value.substring(PREFIX.length)
            val raw = Base32.decode(encoded)
            if (raw.size != RAW_LENGTH) {
                throw InvalidGenesisException("bad_account_id", "accountId does not carry $RAW_LENGTH bytes")
            }
            // The pattern already excludes the seven non-canonical last characters; re-encoding and
            // comparing is the rule ADM-008 decision 2 states, and it also catches any future change to
            // either routine.
            if (Base32.encode(raw) != encoded) {
                throw InvalidGenesisException("non_canonical_account_id", "accountId is not in canonical form")
            }
            if (raw[0] != FORMAT_VERSION) {
                throw InvalidGenesisException("unknown_account_id_version", "unknown accountId format version")
            }
            requireKnownClass(raw[1])
            return AccountId(value, raw)
        }

        private fun requireKnownClass(rootClass: Byte) {
            if (rootClass != CLASS_GENESIS && rootClass != CLASS_BOOTSTRAP) {
                throw InvalidGenesisException("unknown_root_class", "unknown accountId root class")
            }
        }
    }
}
