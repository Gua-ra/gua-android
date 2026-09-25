/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Ed25519Sign
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.guaresolver.genesis.AccountId
import org.junit.Test

/**
 * GUA FORK: the canonical bytes this client signs (ADM-009 decision 2).
 *
 * The offsets are asserted against the record's own table rather than against the builder that wrote them,
 * because the bytes have to match a Java decoder on the other side; a test that only checked the builder
 * agreed with itself would pass while every record was refused.
 */
class AuthorityRecordCodecTest {
    @Test
    fun `an AdoptRoot is 177 bytes and every field is where the table says`() {
        val record = AuthorityRecordCodec.adoptRoot(
            accountReference = AN_ACCOUNT.rawBytes(),
            deviceKey = DEVICE_KEY,
            recoveryAuthorityKey = RECOVERY_KEY,
            label = "Pixel 9",
            entropy = ENTROPY,
        )

        assertThat(record).hasLength(177)
        assertThat(record.copyOfRange(0, 4)).isEqualTo("GUAA".toByteArray(Charsets.US_ASCII))
        assertThat(record[4].toInt()).isEqualTo(1)
        assertThat(record[5].toInt()).isEqualTo(1)
        assertThat(record.copyOfRange(6, 40)).isEqualTo(AN_ACCOUNT.rawBytes())
        // The first record's prevHash is 32 zeros and its seq is 1, and neither is a caller's choice.
        assertThat(record.copyOfRange(40, 72)).isEqualTo(ByteArray(32))
        assertThat(record.copyOfRange(72, 80)).isEqualTo(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1))
        assertThat(record.copyOfRange(80, 112)).isEqualTo(DEVICE_KEY)
        assertThat(record[112].toInt()).isEqualTo(AuthorityRecord.RECOVERY_FRAMEWORK_COMMITTED_KEY)
        assertThat(record.copyOfRange(113, 145)).isEqualTo(RECOVERY_KEY)
        assertThat(record.copyOfRange(145, 161)).isEqualTo("Pixel 9".toByteArray(Charsets.UTF_8).copyOf(16))
        assertThat(record.copyOfRange(161, 177)).isEqualTo(ENTROPY)
    }

    @Test
    fun `a DeviceGrant is 161 bytes and names the key that authorized it`() {
        val record = AuthorityRecordCodec.deviceGrant(
            accountReference = AN_ACCOUNT.rawBytes(),
            prevHash = A_HEAD_HASH,
            seq = 4,
            granteeDeviceKey = GRANTEE_KEY,
            label = "Pixel Tablet",
            authorizingKey = DEVICE_KEY,
        )

        assertThat(record).hasLength(161)
        assertThat(record.copyOfRange(0, 4)).isEqualTo("GUAD".toByteArray(Charsets.US_ASCII))
        assertThat(record.copyOfRange(40, 72)).isEqualTo(A_HEAD_HASH)
        assertThat(record.copyOfRange(72, 80)).isEqualTo(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 4))
        assertThat(record.copyOfRange(80, 112)).isEqualTo(GRANTEE_KEY)
        // No grant flag is defined, so the byte is zero and a decoder that sees anything else refuses.
        assertThat(record[112].toInt()).isEqualTo(0)
        assertThat(record.copyOfRange(113, 129)).isEqualTo("Pixel Tablet".toByteArray(Charsets.UTF_8).copyOf(16))
        // authorizingKey, inside the hashed bytes, so a log leaf commits WHO authorized this.
        assertThat(record.copyOfRange(129, 161)).isEqualTo(DEVICE_KEY)
    }

    @Test
    fun `an all-zero key is refused separately from a key that is not a curve point`() {
        val zero = refusalOf {
            AuthorityRecordCodec.adoptRoot(AN_ACCOUNT.rawBytes(), ByteArray(32), RECOVERY_KEY, "phone")
        }
        // The all-zero encoding decodes to a valid low-order point, so point decoding alone would let it
        // through. That is why ADM-008 decision 1 lists the two rules separately.
        assertThat(zero?.reason).isEqualTo("zero_device_key")

        val offCurve = refusalOf {
            AuthorityRecordCodec.adoptRoot(AN_ACCOUNT.rawBytes(), NOT_A_POINT, RECOVERY_KEY, "phone")
        }
        assertThat(offCurve?.reason).isEqualTo("invalid_device_key")
    }

    @Test
    fun `a recovery key equal to the device key is refused`() {
        val refusal = refusalOf {
            AuthorityRecordCodec.adoptRoot(AN_ACCOUNT.rawBytes(), DEVICE_KEY, DEVICE_KEY, "phone")
        }

        assertThat(refusal?.reason).isEqualTo("duplicate_keys")
    }

    @Test
    fun `a device cannot grant authority to the key it signs with`() {
        val refusal = refusalOf {
            AuthorityRecordCodec.deviceGrant(
                accountReference = AN_ACCOUNT.rawBytes(),
                prevHash = A_HEAD_HASH,
                seq = 2,
                granteeDeviceKey = DEVICE_KEY,
                label = "Pixel Tablet",
                authorizingKey = DEVICE_KEY,
            )
        }

        // Copying one key to every device is what makes revocation meaningless, so the one shape of it this
        // client could produce is refused before it is signed.
        assertThat(refusal?.reason).isEqualTo("duplicate_keys")
    }

    @Test
    fun `a label is zero-padded, never truncated, and never empty`() {
        val padded = AuthorityRecordCodec.labelBytes("Pixel")
        assertThat(padded).hasLength(16)
        assertThat(padded.copyOfRange(5, 16)).isEqualTo(ByteArray(11))

        // A 16-character label with multi-byte characters is more than 16 bytes, and cutting it would put
        // half a character in the notification that names it.
        assertThat(refusalOf { AuthorityRecordCodec.labelBytes("Telefone da casa") }?.reason).isNull()
        assertThat(refusalOf { AuthorityRecordCodec.labelBytes(LONG_LABEL) }?.reason).isEqualTo("label_too_long")
        assertThat(refusalOf { AuthorityRecordCodec.labelBytes("") }?.reason).isEqualTo("empty_label")
        assertThat(refusalOf { AuthorityRecordCodec.labelBytes(LABEL_WITH_A_ZERO) }?.reason)
            .isEqualTo("non_canonical_label")
    }

    @Test
    fun `an empty chain head hex decodes to the 32 zero bytes`() {
        assertThat(AuthorityRecordCodec.prevHashFromHex("0".repeat(64))).isEqualTo(ByteArray(32))
        assertThat(AuthorityRecordCodec.prevHashFromHex(A_HEAD_HASH_HEX)).isEqualTo(A_HEAD_HASH)
        assertThat(refusalOf { AuthorityRecordCodec.prevHashFromHex("abc") }?.reason).isEqualTo("bad_prev_hash")
        assertThat(refusalOf { AuthorityRecordCodec.prevHashFromHex("z".repeat(64)) }?.reason)
            .isEqualTo("bad_prev_hash")
    }

    @Test
    fun `two adoptions over the same keys differ, because every record carries fresh entropy`() {
        val first = AuthorityRecordCodec.adoptRoot(AN_ACCOUNT.rawBytes(), DEVICE_KEY, RECOVERY_KEY, "phone")
        val second = AuthorityRecordCodec.adoptRoot(AN_ACCOUNT.rawBytes(), DEVICE_KEY, RECOVERY_KEY, "phone")

        assertThat(first).isNotEqualTo(second)
        assertThat(AuthorityRecordCodec.hash(first)).hasLength(32)
    }

    @Test
    fun `a revocation names one of the four defined reasons and nothing else`() {
        val record = AuthorityRecordCodec.deviceRevoke(
            accountReference = AN_ACCOUNT.rawBytes(),
            prevHash = A_HEAD_HASH,
            seq = 5,
            deviceKey = GRANTEE_KEY,
            reason = AuthorityRecord.REASON_LOST,
            authorizingKey = DEVICE_KEY,
        )

        assertThat(record).hasLength(145)
        assertThat(record.copyOfRange(80, 112)).isEqualTo(GRANTEE_KEY)
        assertThat(record[112].toInt()).isEqualTo(AuthorityRecord.REASON_LOST)
        assertThat(record.copyOfRange(113, 145)).isEqualTo(DEVICE_KEY)
        assertThat(
            refusalOf {
                AuthorityRecordCodec.deviceRevoke(
                    AN_ACCOUNT.rawBytes(),
                    A_HEAD_HASH,
                    5,
                    GRANTEE_KEY,
                    0x09,
                    DEVICE_KEY
                )
            }?.reason
        ).isEqualTo("unknown_revocation_reason")
    }

    @Test
    fun `a recovery pairs its authorization with its authorizing key in both directions`() {
        // Under 0x01 the record names the recovery key that signs it, which is the rank-2 record an intruder
        // holding every device cannot cast.
        val byRecoveryKey = AuthorityRecordCodec.authorityRecovery(
            accountReference = AN_ACCOUNT.rawBytes(),
            prevHash = A_HEAD_HASH,
            seq = 2,
            deviceKey = GRANTEE_KEY,
            recoveryAuthorityKey = RECOVERY_KEY,
            label = "new phone",
            authorization = AuthorityRecord.AUTHORIZATION_RECOVERY_KEY,
            authorizingKey = DEVICE_KEY,
            entropy = ENTROPY,
        )
        assertThat(byRecoveryKey).hasLength(209)
        assertThat(byRecoveryKey[176].toInt()).isEqualTo(AuthorityRecord.AUTHORIZATION_RECOVERY_KEY)
        assertThat(byRecoveryKey.copyOfRange(177, 209)).isEqualTo(DEVICE_KEY)

        // Under 0x02 the field is 32 zero bytes, because there is no key: the authorization is a completed
        // account recovery, and any active device may veto it immediately.
        val throughAccountRecovery = AuthorityRecordCodec.authorityRecovery(
            accountReference = AN_ACCOUNT.rawBytes(),
            prevHash = A_HEAD_HASH,
            seq = 2,
            deviceKey = GRANTEE_KEY,
            recoveryAuthorityKey = RECOVERY_KEY,
            label = "new phone",
            authorization = AuthorityRecord.AUTHORIZATION_ACCOUNT_RECOVERY,
            authorizingKey = null,
            entropy = ENTROPY,
        )
        assertThat(throughAccountRecovery.copyOfRange(177, 209)).isEqualTo(ByteArray(32))

        assertThat(
            refusalOf {
                AuthorityRecordCodec.authorityRecovery(
                    AN_ACCOUNT.rawBytes(),
                    A_HEAD_HASH,
                    2,
                    GRANTEE_KEY,
                    RECOVERY_KEY,
                    "phone",
                    AuthorityRecord.AUTHORIZATION_ACCOUNT_RECOVERY,
                    DEVICE_KEY,
                    ENTROPY
                )
            }?.reason
        ).isEqualTo("authorizing_key_not_permitted")
        assertThat(
            refusalOf {
                AuthorityRecordCodec.authorityRecovery(
                    AN_ACCOUNT.rawBytes(),
                    A_HEAD_HASH,
                    2,
                    GRANTEE_KEY,
                    RECOVERY_KEY,
                    "phone",
                    AuthorityRecord.AUTHORIZATION_RECOVERY_KEY,
                    null,
                    ENTROPY
                )
            }?.reason
        ).isEqualTo("authorizing_key_required")
    }

    @Test
    fun `an objection names the record it cancels and carries that record's position`() {
        val opposed = ByteArray(32) { (it + 7).toByte() }
        val record = AuthorityRecordCodec.oppose(
            accountReference = AN_ACCOUNT.rawBytes(),
            prevHash = A_HEAD_HASH,
            seq = 4,
            opposedRecordHash = opposed,
            authorizingKey = DEVICE_KEY,
        )

        assertThat(record).hasLength(144)
        assertThat(record.copyOfRange(40, 72)).isEqualTo(A_HEAD_HASH)
        assertThat(record.copyOfRange(72, 80)).isEqualTo(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 4))
        assertThat(record.copyOfRange(80, 112)).isEqualTo(opposed)
        assertThat(record.copyOfRange(112, 144)).isEqualTo(DEVICE_KEY)

        // An objection to nothing is refused here as well as by the server: it would name whatever record
        // happened to be pending when it arrived.
        assertThat(
            refusalOf {
                AuthorityRecordCodec.oppose(AN_ACCOUNT.rawBytes(), A_HEAD_HASH, 4, ByteArray(32), DEVICE_KEY)
            }?.reason
        ).isEqualTo("zero_opposed_record")
    }

    @Test
    fun `the decoder reads back what the builders wrote`() {
        val grant = AuthorityRecordCodec.deviceGrant(
            accountReference = AN_ACCOUNT.rawBytes(),
            prevHash = A_HEAD_HASH,
            seq = 9,
            granteeDeviceKey = GRANTEE_KEY,
            label = "Pixel Tablet",
            authorizingKey = DEVICE_KEY,
        )

        val parsed = AuthorityRecordCodec.parse(grant)

        assertThat(parsed.type).isEqualTo(AuthorityRecordType.DEVICE_GRANT)
        assertThat(parsed.seq).isEqualTo(9)
        assertThat(parsed.prevHash).isEqualTo(A_HEAD_HASH)
        assertThat(parsed.accountReference).isEqualTo(AN_ACCOUNT.rawBytes())
        assertThat(parsed.deviceKey).isEqualTo(GRANTEE_KEY)
        assertThat(parsed.authorizingKey).isEqualTo(DEVICE_KEY)
    }

    private fun refusalOf(block: () -> Unit): InvalidAuthorityRecordException? =
        runCatchingExceptions(block).exceptionOrNull() as? InvalidAuthorityRecordException

    private companion object {
        private val DEVICE_KEY = Ed25519Sign.KeyPair.newKeyPair().publicKey
        private val RECOVERY_KEY = Ed25519Sign.KeyPair.newKeyPair().publicKey
        private val GRANTEE_KEY = Ed25519Sign.KeyPair.newKeyPair().publicKey

        /** Every byte 0xFF is not a curve point, while being obviously not all zero. */
        private val NOT_A_POINT = ByteArray(32) { 0xFF.toByte() }

        private val ENTROPY = ByteArray(16) { it.toByte() }
        private val AN_ACCOUNT = AccountId.derive(AccountId.CLASS_BOOTSTRAP, "an account".toByteArray())
        private val A_HEAD_HASH = ByteArray(32) { (it + 1).toByte() }
        private val A_HEAD_HASH_HEX = A_HEAD_HASH.joinToString("") { "%02x".format(it) }
        private const val LONG_LABEL = "a phone with a very long name"
        private val LABEL_WITH_A_ZERO = "a" + Char(0) + "b"
    }
}
