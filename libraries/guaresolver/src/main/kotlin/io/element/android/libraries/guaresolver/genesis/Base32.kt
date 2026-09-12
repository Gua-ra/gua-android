/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * GUA FORK: RFC 4648 base32, lowercase and unpadded, which is the spelling ADM-008 decision 2 fixes
 * for an accountId. Port of the identity-service `Base32`, byte for byte.
 *
 * The decoder is strict in each direction an ambiguity could enter: only the lowercase alphabet (no
 * uppercase, no padding, no "extended hex" alphabet), only a character count an unpadded encoding can
 * actually produce, and only zero trailing bits. Those are the three ways a decoder that "helpfully"
 * accepts more would give one byte string several spellings, which ADM-001 L4 forbids for anything a
 * signature or a permanent identifier covers.
 */
internal object Base32 {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

    /** Reverse lookup, -1 for every character outside the alphabet. */
    private val values = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { index, character -> this[character.code] = index }
    }

    /** Encodes [data] as lowercase unpadded base32. */
    fun encode(data: ByteArray): String {
        val out = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = buffer shl 8 or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                val shift = bits - 5
                out.append(ALPHABET[buffer ushr shift and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) {
            // Left-over bits are left-aligned and zero-padded on the right.
            val shift = 5 - bits
            out.append(ALPHABET[buffer shl shift and 0x1F])
        }
        return out.toString()
    }

    /**
     * Decodes lowercase unpadded base32.
     *
     * @throws InvalidGenesisException with reason `bad_base32` on any character outside the alphabet,
     * a character count no unpadded encoding produces, or non-zero trailing bits.
     */
    fun decode(encoded: String): ByteArray {
        val remainder = encoded.length % 8
        // 1, 3 and 6 left-over characters cannot come out of any byte string.
        if (remainder == 1 || remainder == 3 || remainder == 6) {
            throw InvalidGenesisException("bad_base32", "base32 length is not a valid unpadded length")
        }
        val out = ByteArray(encoded.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (character in encoded) {
            val value = if (character.code < values.size) values[character.code] else -1
            if (value < 0) {
                throw InvalidGenesisException("bad_base32", "base32 value has a character outside the alphabet")
            }
            buffer = buffer shl 5 or value
            bits += 5
            if (bits >= 8) {
                val shift = bits - 8
                out[index++] = (buffer ushr shift and 0xFF).toByte()
                bits -= 8
            }
        }
        // Whatever is left over is padding and must be zero, or one byte string has several spellings.
        if (bits > 0) {
            val mask = (1 shl bits) - 1
            if (buffer and mask != 0) {
                throw InvalidGenesisException("bad_base32", "base32 value has non-zero trailing bits")
            }
        }
        return out
    }
}
