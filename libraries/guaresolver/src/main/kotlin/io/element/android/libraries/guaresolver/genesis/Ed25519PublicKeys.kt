/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import java.math.BigInteger

/** The platform provider has no Ed25519 at this module's minSdk, so points are decoded here, refusing what identity-service refuses. */
internal object Ed25519PublicKeys {
    const val RAW_PUBLIC_KEY_LENGTH = 32
    const val SIGNATURE_LENGTH = 64

    // Not BigInteger.TWO: that field is API 33, above this module's minSdk.
    private val TWO: BigInteger = BigInteger.valueOf(2)

    private val P: BigInteger = TWO.pow(255) - BigInteger.valueOf(19)

    /** The curve constant d = -121665/121666 mod p. */
    private val D: BigInteger = BigInteger("37095705934669439343138083508754565189542113879843219016388785533085940283555")

    private val SQRT_MINUS_ONE: BigInteger = TWO.modPow((P - BigInteger.ONE) / BigInteger.valueOf(4), P)

    /** The y-coordinates of the eight points of order 1, 2, 4 and 8. */
    private val SMALL_ORDER_Y: Set<BigInteger> = setOf(
        BigInteger.ONE,
        P - BigInteger.ONE,
        BigInteger.ZERO,
        BigInteger("05fc536d880238b13933c6d305acdfd5f098eff289f4c345b027b2c28f95e826", 16),
        BigInteger("7a03ac9277fdc74ec6cc392cfa53202a0f67100d760b3cba4fd84d3d706a17c7", 16),
    )

    fun isAllZero(value: ByteArray): Boolean {
        var accumulator = 0
        for (byte in value) {
            accumulator = accumulator or byte.toInt()
        }
        return accumulator == 0
    }

    /** True when the raw bytes are the canonical encoding of a curve point (RFC 8032 section 5.1.3). */
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
                // The other root.
                x = x.multiply(SQRT_MINUS_ONE).mod(P)
                if (v.multiply(x).mod(P).multiply(x).mod(P) != u) return false
            }
            else -> return false
        }

        // x = 0 has no negative, so that encoding names no point.
        return !(x.signum() == 0 && signBit != 0)
    }

    /** True for the points of order 1, 2, 4 and 8, under which signatures can be forged. */
    fun hasSmallOrder(rawPublicKey: ByteArray): Boolean =
        rawPublicKey.size == RAW_PUBLIC_KEY_LENGTH &&
            BigInteger(1, rawPublicKey.reversedArray()).clearBit(255) in SMALL_ORDER_Y

    fun require(rawPublicKey: ByteArray, reason: String) {
        if (rawPublicKey.size != RAW_PUBLIC_KEY_LENGTH) {
            throw InvalidGenesisException(reason, "Ed25519 public key is not $RAW_PUBLIC_KEY_LENGTH bytes")
        }
        if (!isOnCurve(rawPublicKey)) {
            throw InvalidGenesisException(reason, "Ed25519 public key does not decode to a curve point")
        }
        if (hasSmallOrder(rawPublicKey)) {
            throw InvalidGenesisException(reason, "Ed25519 public key has small order")
        }
    }
}
