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

/** GUA FORK: the one preimage rule, and the browser-approval preimage (ADM-009 decisions 2 and 6). */
class AuthorityProofsTest {
    @Test
    fun `a record preimage is the magic, then the challenge, then the canonical bytes`() {
        val record = AuthorityRecordCodec.adoptRoot(
            accountReference = AN_ACCOUNT.rawBytes(),
            deviceKey = DEVICE_KEY,
            recoveryAuthorityKey = RECOVERY_KEY,
            label = "phone",
        )

        val preimage = AuthorityProofs.recordPreimage(AuthorityRecordType.ADOPT_ROOT, A_CHALLENGE, record)

        assertThat(preimage).hasLength(4 + 32 + 177)
        assertThat(preimage.copyOfRange(0, 4)).isEqualTo("GUAA".toByteArray(Charsets.US_ASCII))
        assertThat(preimage.copyOfRange(4, 36)).isEqualTo(A_CHALLENGE)
        assertThat(preimage.copyOfRange(36, preimage.size)).isEqualTo(record)
    }

    @Test
    fun `the magic is the domain, so a grant is signed under its own type`() {
        val grant = AuthorityRecordCodec.deviceGrant(
            accountReference = AN_ACCOUNT.rawBytes(),
            prevHash = ByteArray(32),
            seq = 2,
            granteeDeviceKey = GRANTEE_KEY,
            label = "tablet",
            authorizingKey = DEVICE_KEY,
        )

        val asGrant = AuthorityProofs.recordPreimage(AuthorityRecordType.DEVICE_GRANT, A_CHALLENGE, grant)

        // No record can be replayed as another type, because the type's magic leads its own preimage.
        assertThat(asGrant.copyOfRange(0, 4)).isEqualTo("GUAD".toByteArray(Charsets.US_ASCII))
        assertThat(asGrant.copyOfRange(36, asGrant.size)).isEqualTo(grant)
    }

    @Test
    fun `a different challenge is a different preimage, so a signature is not transferable`() {
        val record = AuthorityRecordCodec.adoptRoot(AN_ACCOUNT.rawBytes(), DEVICE_KEY, RECOVERY_KEY, "phone")

        val first = AuthorityProofs.recordPreimage(AuthorityRecordType.ADOPT_ROOT, A_CHALLENGE, record)
        val second = AuthorityProofs.recordPreimage(
            AuthorityRecordType.ADOPT_ROOT,
            ByteArray(32) { 9 },
            record,
        )

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `a challenge that is not 32 bytes is refused rather than padded`() {
        val record = AuthorityRecordCodec.adoptRoot(AN_ACCOUNT.rawBytes(), DEVICE_KEY, RECOVERY_KEY, "phone")

        val refusal = runCatchingExceptions {
            AuthorityProofs.recordPreimage(AuthorityRecordType.ADOPT_ROOT, ByteArray(31), record)
        }.exceptionOrNull() as? InvalidAuthorityRecordException

        assertThat(refusal?.reason).isEqualTo("wrong_length")
    }

    @Test
    fun `an approval preimage is the domain, the account, the approval, the digest and the challenge`() {
        val approvalId = ByteArray(16) { it.toByte() }
        val digest = ByteArray(32) { (it + 3).toByte() }

        val preimage = AuthorityProofs.approvalPreimage(
            accountReference = AN_ACCOUNT.rawBytes(),
            approvalId = approvalId,
            actionDigest = digest,
            challenge = A_CHALLENGE,
        )

        assertThat(preimage).hasLength(AuthorityProofs.APPROVAL_PREIMAGE_LENGTH)
        assertThat(preimage).hasLength(25 + 34 + 16 + 32 + 32)
        val domain = AuthorityProofs.APPROVAL_DOMAIN.toByteArray(Charsets.US_ASCII)
        assertThat(domain).hasLength(25)
        assertThat(preimage.copyOfRange(0, 25)).isEqualTo(domain)
        assertThat(preimage.copyOfRange(25, 59)).isEqualTo(AN_ACCOUNT.rawBytes())
        assertThat(preimage.copyOfRange(59, 75)).isEqualTo(approvalId)
        assertThat(preimage.copyOfRange(75, 107)).isEqualTo(digest)
        assertThat(preimage.copyOfRange(107, 139)).isEqualTo(A_CHALLENGE)
    }

    @Test
    fun `every approval field is fixed length, so none can be shifted into another`() {
        val refusal = runCatchingExceptions {
            AuthorityProofs.approvalPreimage(
                accountReference = AN_ACCOUNT.rawBytes(),
                approvalId = ByteArray(15),
                actionDigest = ByteArray(32),
                challenge = A_CHALLENGE,
            )
        }.exceptionOrNull() as? InvalidAuthorityRecordException

        assertThat(refusal?.reason).isEqualTo("wrong_length")
    }

    private companion object {
        private val DEVICE_KEY = Ed25519Sign.KeyPair.newKeyPair().publicKey
        private val RECOVERY_KEY = Ed25519Sign.KeyPair.newKeyPair().publicKey
        private val GRANTEE_KEY = Ed25519Sign.KeyPair.newKeyPair().publicKey
        private val A_CHALLENGE = ByteArray(32) { (it * 7).toByte() }
        private val AN_ACCOUNT = AccountId.derive(AccountId.CLASS_BOOTSTRAP, "an account".toByteArray())
    }
}
