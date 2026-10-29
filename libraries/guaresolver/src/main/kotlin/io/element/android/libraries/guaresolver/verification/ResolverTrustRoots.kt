/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

/** A published verification key: its id as the signatures name it, and the raw Ed25519 public key. */
internal class TrustedKey(val id: String, val rawPublicKey: ByteArray) {
    fun verifies(message: ByteArray, signatureB64: String?): Boolean = Ed25519Signatures.verify(rawPublicKey, message, signatureB64)

    companion object {
        fun parse(id: String, publicKeySpkiB64: String): TrustedKey? =
            Ed25519Signatures.rawPublicKey(publicKeySpkiB64)?.let { TrustedKey(id, it) }
    }
}

/** A detached signature as every signed object carries it: the signing key's id and the padded base64 signature. */
internal data class KeyedSignature(val keyId: String?, val signatureB64: String?)

/**
 * One vote per trusted key id over the same canonical bytes, as the resolver's `RosterVerifier` and
 * `RoutingPolicyVerifier` count them. [knownKeyIds] only serves the reason given for a shortfall.
 */
internal data class ThresholdCheck(val valid: Int, val threshold: Int, val knownKeyIds: Int) {
    val met: Boolean
        get() = valid >= threshold

    val failureReason: String
        get() = when {
            knownKeyIds == 0 -> VerificationReason.UNKNOWN_KEY_ID
            valid == 0 -> VerificationReason.BAD_SIGNATURE
            else -> VerificationReason.THRESHOLD_NOT_MET
        }

    companion object {
        fun count(keys: List<TrustedKey>, threshold: Int, canonical: ByteArray, signatures: List<KeyedSignature>): ThresholdCheck {
            val byId = keys.associateBy { it.id }
            val counted = mutableSetOf<String>()
            var valid = 0
            var known = 0
            for (signature in signatures) {
                val key = signature.keyId?.let { byId[it] } ?: continue
                known++
                if (key.id !in counted && key.verifies(canonical, signature.signatureB64)) {
                    counted += key.id
                    valid++
                }
            }
            return ThresholdCheck(valid = valid, threshold = maxOf(1, threshold), knownKeyIds = known)
        }
    }
}

/**
 * The keys a deployment's verifier trusts. The policy keys are listed explicitly: there is no fallback to
 * the authority keys, so an empty list verifies no policy.
 */
internal data class ResolverTrustRoots(
    val authorityKeys: List<TrustedKey>,
    val authorityThreshold: Int,
    val policyKeys: List<TrustedKey>,
    val policyThreshold: Int,
) {
    companion object {
        /**
         * Whitespace-separated tokens: `authority:<key id>:<base64 SPKI>`, `policy:<key id>:<base64 SPKI>`,
         * `authority-threshold:<n>` and `policy-threshold:<n>`; thresholds default to 1.
         *
         * @throws IllegalArgumentException on a token that is not one of those, or a key that is not an Ed25519 SPKI.
         */
        fun parse(value: String): ResolverTrustRoots {
            val authorityKeys = mutableListOf<TrustedKey>()
            val policyKeys = mutableListOf<TrustedKey>()
            var authorityThreshold = 1
            var policyThreshold = 1
            for (token in value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val parts = token.split(':')
                when {
                    parts.size == 3 && parts[0] == "authority" -> authorityKeys += key(parts[1], parts[2])
                    parts.size == 3 && parts[0] == "policy" -> policyKeys += key(parts[1], parts[2])
                    parts.size == 2 && parts[0] == "authority-threshold" -> authorityThreshold = threshold(parts[1])
                    parts.size == 2 && parts[0] == "policy-threshold" -> policyThreshold = threshold(parts[1])
                    else -> throw IllegalArgumentException("unrecognised trust root token")
                }
            }
            return ResolverTrustRoots(authorityKeys, authorityThreshold, policyKeys, policyThreshold)
        }

        private fun key(id: String, publicKeySpkiB64: String): TrustedKey {
            require(id.isNotEmpty()) { "a trusted key needs an id" }
            return requireNotNull(TrustedKey.parse(id, publicKeySpkiB64)) { "a trusted key must be an Ed25519 SubjectPublicKeyInfo" }
        }

        private fun threshold(value: String): Int {
            val threshold = value.toIntOrNull()
            require(threshold != null && threshold >= 1) { "a threshold is a positive integer" }
            return threshold
        }
    }
}
