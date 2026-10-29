/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** A value with no canonical encoding; the object carrying it is refused, never repaired. */
internal class CanonicalEncodingException(message: String) : Exception(message)

/**
 * The `gua-lp.v1` length-prefixed encoding of the signed federation objects: u32 big-endian lengths,
 * int64 big-endian integers, a presence byte for optionals, sets sorted by unsigned UTF-8 byte order, no
 * delimiters. Port of the resolver's `CanonicalEncoder`.
 */
internal class CanonicalEncoder {
    private val out = ByteArrayOutputStream()

    fun bytes(value: ByteArray): CanonicalEncoder {
        writeU32(value.size)
        out.write(value)
        return this
    }

    fun string(value: String): CanonicalEncoder = bytes(utf8(value))

    fun int64(value: Long): CanonicalEncoder {
        out.write(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array())
        return this
    }

    /** Absent and empty are different bytes. */
    fun optionalString(value: String?): CanonicalEncoder {
        if (value == null) {
            out.write(0)
            return this
        }
        out.write(1)
        return string(value)
    }

    fun stringSet(values: Collection<String>): CanonicalEncoder {
        val encoded = values.map { utf8(it) }.sortedWith { a, b -> compareUnsigned(a, b) }
        for (i in 1 until encoded.size) {
            if (encoded[i - 1].contentEquals(encoded[i])) throw CanonicalEncodingException("duplicate set element")
        }
        writeU32(encoded.size)
        encoded.forEach { bytes(it) }
        return this
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun writeU32(length: Int) {
        if (length < 0) throw CanonicalEncodingException("length out of range")
        out.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(length).array())
    }

    companion object {
        /** Every object starts with its schema tag. */
        fun begin(schemaTag: String): CanonicalEncoder {
            if (schemaTag.isEmpty()) throw CanonicalEncodingException("schema tag is required")
            return CanonicalEncoder().string(schemaTag)
        }

        private fun utf8(value: String): ByteArray {
            try {
                val buffer = Charsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value))
                return ByteArray(buffer.remaining()).also { buffer.get(it) }
            } catch (e: CharacterCodingException) {
                throw CanonicalEncodingException("string is not valid Unicode")
            }
        }

        private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val difference = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
                if (difference != 0) return difference
            }
            return a.size - b.size
        }
    }
}
