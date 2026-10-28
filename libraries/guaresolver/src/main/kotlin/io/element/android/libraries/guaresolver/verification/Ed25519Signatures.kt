/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalEncodingApi::class)

package io.element.android.libraries.guaresolver.verification

import com.google.crypto.tink.subtle.Ed25519Verify
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.guaresolver.genesis.Ed25519PublicKeys
import java.security.GeneralSecurityException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Ed25519 as the resolver exchanges it: public keys as base64 X.509 SubjectPublicKeyInfo (44 bytes, a
 * fixed 12-byte prefix then the raw key), signatures as padded base64 of 64 bytes. Verification is Tink's,
 * because the platform provider has no Ed25519 at this module's minSdk.
 */
internal object Ed25519Signatures {
    private val SPKI_PREFIX = Hex.decode("302a300506032b6570032100")!!
    private val SPKI_LENGTH = SPKI_PREFIX.size + Ed25519PublicKeys.RAW_PUBLIC_KEY_LENGTH

    /** The raw key bytes of a base64 SPKI, or null unless it is a well-formed Ed25519 public key on the curve. */
    fun rawPublicKey(publicKeySpkiB64: String): ByteArray? {
        val spki = decode(publicKeySpkiB64) ?: return null
        if (spki.size != SPKI_LENGTH || !spki.copyOfRange(0, SPKI_PREFIX.size).contentEquals(SPKI_PREFIX)) return null
        val raw = spki.copyOfRange(SPKI_PREFIX.size, SPKI_LENGTH)
        if (Ed25519PublicKeys.isAllZero(raw) || !Ed25519PublicKeys.isOnCurve(raw)) return null
        return raw
    }

    /** False on any malformed input, never an exception. */
    fun verify(rawPublicKey: ByteArray, message: ByteArray, signatureB64: String?): Boolean {
        val signature = signatureB64?.let { decode(it) } ?: return false
        if (signature.size != Ed25519PublicKeys.SIGNATURE_LENGTH) return false
        return try {
            Ed25519Verify(rawPublicKey).verify(signature, message)
            true
        } catch (e: GeneralSecurityException) {
            false
        }
    }

    private fun decode(base64: String): ByteArray? = tryOrNull { Base64.decode(base64) }
}
