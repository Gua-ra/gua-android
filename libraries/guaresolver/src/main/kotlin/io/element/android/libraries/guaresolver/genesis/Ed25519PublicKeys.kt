/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import java.math.BigInteger

/**
 * GUA FORK: raw RFC 8032 Ed25519 public keys, as the genesis objects carry them.
 *
 * ADM-008 decision 1 requires the DECODER itself to refuse a key that fails Ed25519 point decoding, not
 * to defer it to the first verification. identity-service gets that from the JDK provider (Ed25519 is
 * JDK 15+), which Android does not have at this module's minSdk, so the point decoding is done here in
 * arithmetic that behaves identically on every supported API level. The two rules ADM-008 lists stay
 * separate on purpose: [isAllZero] is checked before [isOnCurve], because the all-zero encoding decodes
 * to a valid low-order point and point decoding alone would let it through.
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

    /** True when every byte is zero. ADM-008 decision 1 refuses such a key even though it decodes. */
    fun isAllZero(value: ByteArray): Boolean {
        var accumulator = 0
        for (byte in value) {
            accumulator = accumulator or byte.toInt()
        }
        return accumulator == 0
    }

    /**
     * True when the raw bytes decode to a point on the curve.
     *
     * The encoding is little-endian y with the top bit carrying the sign of x, so a y at or above the
     * field prime is refused first (that is the "y is larger than the field prime" rejection vector),
     * then x is recovered and the curve equation is checked (the "not on the curve" one).
     */
    fun isOnCurve(rawPublicKey: ByteArray): Boolean {
        if (rawPublicKey.size != RAW_PUBLIC_KEY_LENGTH) return false
        val y = BigInteger(1, rawPublicKey.reversedArray()).clearBit(255)
        if (y >= P) return false

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
        return when {
            check == u -> true
            check == u.negate().mod(P) -> {
                // The other root; a point either way, which is what decoding has to establish.
                x = x.multiply(SQRT_MINUS_ONE).mod(P)
                v.multiply(x).mod(P).multiply(x).mod(P) == u
            }
            else -> false
        }
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
