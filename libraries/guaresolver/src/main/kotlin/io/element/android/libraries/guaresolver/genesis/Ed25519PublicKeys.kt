/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import java.math.BigInteger

/**
 * Raw RFC 8032 Ed25519 public keys, as the genesis objects carry them.
 *
 * The decoder itself must refuse a key that fails Ed25519 point decoding, not defer it to the first
 * verification. The identity service gets that from the JDK provider, which Android does not have at
 * this module's minSdk, so point decoding is done here. [isAllZero] is checked before [isOnCurve]
 * because the all-zero encoding decodes to a valid low-order point.
 */
internal object Ed25519PublicKeys {
    const val RAW_PUBLIC_KEY_LENGTH = 32
    const val SIGNATURE_LENGTH = 64

    /** The field prime, 2^255 - 19. */
    private val P: BigInteger = BigInteger.TWO.pow(255) - BigInteger.valueOf(19)

    /** The curve constant d = -121665/121666 mod p. */
    private val D: BigInteger = BigInteger("37095705934669439343138083508754565189542113879843219016388785533085940283555")

    /** A square root of -1 mod p, used to recover the other root. */
    private val SQRT_MINUS_ONE: BigInteger = BigInteger.TWO.modPow((P - BigInteger.ONE) / BigInteger.valueOf(4), P)

    /** True when every byte is zero. Such a key is refused even though it decodes. */
    fun isAllZero(value: ByteArray): Boolean {
        var accumulator = 0
        for (byte in value) {
            accumulator = accumulator or byte.toInt()
        }
        return accumulator == 0
    }

    /**
     * True when the raw bytes decode to a point on the curve, per RFC 8032 section 5.1.3.
     *
     * The encoding is little-endian y with the top bit carrying the sign of x. A y at or above the field
     * prime is refused first, then x is recovered and the curve equation is checked. Decoding also fails
     * when the recovered x is zero and the sign bit asks for the negative root. The server's decoder
     * enforces that rule, so this one must too.
     */
    fun isOnCurve(rawPublicKey: ByteArray): Boolean {
        if (rawPublicKey.size != RAW_PUBLIC_KEY_LENGTH) return false
        val y = BigInteger(1, rawPublicKey.reversedArray()).clearBit(255)
        if (y >= P) return false
        val signBit = rawPublicKey[RAW_PUBLIC_KEY_LENGTH - 1].toInt() and 0x80

        val ySquared = y.multiply(y).mod(P)
        val u = ySquared.subtract(BigInteger.ONE).mod(P)
        val v = D.multiply(ySquared).add(BigInteger.ONE).mod(P)
        if (v.signum() == 0) return false

        // Candidate root of x^2 = u/v, per RFC 8032 section 5.1.3.
        val vCubed = v.modPow(BigInteger.valueOf(3), P)
        val vSeventh = v.modPow(BigInteger.valueOf(7), P)
        val exponent = (P - BigInteger.valueOf(5)) / BigInteger.valueOf(8)
        var x = u.multiply(vCubed).mod(P).multiply(u.multiply(vSeventh).mod(P).modPow(exponent, P)).mod(P)

        val check = v.multiply(x).mod(P).multiply(x).mod(P)
        when {
            check == u -> Unit
            check == u.negate().mod(P) -> {
                // The other root; a point either way, which is what decoding has to establish.
                x = x.multiply(SQRT_MINUS_ONE).mod(P)
                if (v.multiply(x).mod(P).multiply(x).mod(P) != u) return false
            }
            else -> return false
        }

        // x = 0 has one root, so an encoding that asks for its negative names no point.
        // Only y = 1 and y = p - 1 reach this, and the server's decoder refuses both.
        return !(x.signum() == 0 && signBit != 0)
    }

    /**
     * Refuses a key that is not a curve point, with the caller's rejection [reason].
     *
     * @throws InvalidGenesisException when the bytes are not a well-formed Ed25519 public key.
     */
    fun require(rawPublicKey: ByteArray, reason: String) {
        if (rawPublicKey.size != RAW_PUBLIC_KEY_LENGTH) {
            throw InvalidGenesisException(reason, "Ed25519 public key is not $RAW_PUBLIC_KEY_LENGTH bytes")
        }
        if (!isOnCurve(rawPublicKey)) {
            throw InvalidGenesisException(reason, "Ed25519 public key does not decode to a curve point")
        }
    }
}
