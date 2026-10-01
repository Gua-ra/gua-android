/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import java.security.MessageDigest

/**
 * `"ga1" || base32(0x01 || class || SHA-256(canonical bytes))`, hashed over the bytes as received.
 * An accountId has exactly one spelling: [parse] decodes, re-encodes and compares.
 */
class AccountId private constructor(
    val value: String,
    private val raw: ByteArray,
) {
    fun rawBytes(): ByteArray = raw.copyOf()

    val rootClass: Byte get() = raw[1]

    val isGenesisRooted: Boolean get() = rootClass == CLASS_GENESIS

    override fun equals(other: Any?): Boolean = other is AccountId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        const val PREFIX = "ga1"

        const val FORMAT_VERSION: Byte = 0x01

        const val CLASS_GENESIS: Byte = 0x01

        const val CLASS_BOOTSTRAP: Byte = 0x00

        const val RAW_LENGTH = 34

        const val ENCODED_LENGTH = 55

        const val LENGTH = 58

        const val CANONICAL_PATTERN = "^ga1[a-z2-7]{54}[aiqy]$"

        private val canonical = Regex(CANONICAL_PATTERN)

        fun derive(rootClass: Byte, canonicalBytes: ByteArray): AccountId {
            requireKnownClass(rootClass)
            val raw = ByteArray(RAW_LENGTH)
            raw[0] = FORMAT_VERSION
            raw[1] = rootClass
            MessageDigest.getInstance("SHA-256").digest(canonicalBytes).copyInto(raw, 2)
            return AccountId(PREFIX + Base32.encode(raw), raw)
        }

        fun parse(value: String): AccountId {
            if (!canonical.matches(value)) {
                throw InvalidGenesisException("bad_account_id", "accountId does not match the canonical pattern")
            }
            val encoded = value.substring(PREFIX.length)
            val raw = Base32.decode(encoded)
            if (raw.size != RAW_LENGTH) {
                throw InvalidGenesisException("bad_account_id", "accountId does not carry $RAW_LENGTH bytes")
            }
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
