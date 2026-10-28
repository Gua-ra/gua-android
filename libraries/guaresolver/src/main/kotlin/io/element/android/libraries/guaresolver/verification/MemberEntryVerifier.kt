/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import io.element.android.libraries.core.data.tryOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

/** The `gua-member-entry.v1` bytes a homeserver signs about its own entry. */
internal object CanonicalMemberEntry {
    const val SCHEMA = "gua-member-entry.v1"
    const val ALG = "Ed25519"

    fun bytes(homeserver: RosterHomeserver, member: MemberAttestation): ByteArray =
        CanonicalEncoder.begin(SCHEMA)
            .string(homeserver.id)
            .string(homeserver.serverName)
            .string(homeserver.baseUrl)
            .string(homeserver.masIssuer ?: throw CanonicalEncodingException("masIssuer is required"))
            .string(member.alg)
            .string(homeserver.signingKey ?: throw CanonicalEncodingException("signingKey is required"))
            .string(member.keyId)
            .int64(member.sequence)
            .int64(member.notBefore.toEpochMillis())
            .int64(member.notAfter.toEpochMillis())
            .optionalString(homeserver.region)
            .string(homeserver.searchVisibility ?: "GLOBAL")
            .stringSet(homeserver.searchGroups.orEmpty())
            .toByteArray()
}

/** The last entry a verifier accepted for a homeserver id; v1 keeps none, so the orchestrator passes null. */
internal data class MemberPrior(
    val keyId: String?,
    val signingKey: String?,
    val sequence: Long,
    val entryHash: String?,
)

/**
 * Verifies a homeserver's self-signature over its roster entry, port of the resolver's
 * `MemberEntryVerifier`. The member block is parsed strictly here: a field the signature does not cover
 * refuses the entry instead of being ignored.
 */
internal class MemberEntryVerifier(
    private val maxLifetimeMillis: Long = DEFAULT_MAX_LIFETIME_MILLIS,
) {
    private val strictJson = Json

    fun verify(homeserver: RosterHomeserver, member: JsonObject?, atMillis: Long, prior: MemberPrior? = null): MemberEntryOutcome {
        if (member == null) return MemberEntryOutcome.Unattested
        val attestation = tryOrNull { strictJson.decodeFromJsonElement<MemberAttestation>(member) }
            ?: return MemberEntryOutcome.Invalid(VerificationReason.PARSE_ERROR)
        if (attestation.schema != CanonicalMemberEntry.SCHEMA) return MemberEntryOutcome.Invalid(VerificationReason.UNSUPPORTED_SCHEMA)
        if (attestation.alg != CanonicalMemberEntry.ALG) return MemberEntryOutcome.Invalid(VerificationReason.UNSUPPORTED_ALG)
        if (attestation.keyId.isBlank()) return MemberEntryOutcome.Invalid(VerificationReason.BAD_KEY_ID)
        if (attestation.sequence < 1) return MemberEntryOutcome.Invalid(VerificationReason.BAD_SEQUENCE)
        checkWindow(attestation, atMillis)?.let { return MemberEntryOutcome.Invalid(it) }
        checkSignatureShape(attestation)?.let { return MemberEntryOutcome.Invalid(it) }

        val canonical = try {
            CanonicalMemberEntry.bytes(homeserver, attestation)
        } catch (e: CanonicalEncodingException) {
            return MemberEntryOutcome.Invalid(VerificationReason.NO_CANONICAL_ENCODING)
        }
        val publicKey = homeserver.signingKey?.let { Ed25519Signatures.rawPublicKey(it) }
            ?: return MemberEntryOutcome.Invalid(VerificationReason.BAD_SIGNING_KEY)
        val own = attestation.signatures.firstOrNull { it.keyId == attestation.keyId }
            ?: return MemberEntryOutcome.Invalid(VerificationReason.MISSING_SIGNATURE)
        if (!Ed25519Signatures.verify(publicKey, canonical, own.signatureB64)) {
            return MemberEntryOutcome.Invalid(VerificationReason.BAD_SIGNATURE)
        }
        if (prior != null && !isContinuous(homeserver, attestation, prior, canonical)) {
            return MemberEntryOutcome.Invalid(VerificationReason.KEY_CONTINUITY)
        }
        return MemberEntryOutcome.Valid
    }

    private fun checkWindow(member: MemberAttestation, atMillis: Long): String? {
        val notBefore = tryOrNull { Instant.parse(member.notBefore) } ?: return VerificationReason.PARSE_ERROR
        val notAfter = tryOrNull { Instant.parse(member.notAfter) } ?: return VerificationReason.PARSE_ERROR
        if (notBefore.nano % NANOS_PER_MILLI != 0 || notAfter.nano % NANOS_PER_MILLI != 0) return VerificationReason.BAD_WINDOW
        val notBeforeMillis = notBefore.toEpochMilli()
        val notAfterMillis = notAfter.toEpochMilli()
        if (notAfterMillis <= notBeforeMillis) return VerificationReason.BAD_WINDOW
        if (notAfterMillis - notBeforeMillis > maxLifetimeMillis) return VerificationReason.BAD_WINDOW
        if (atMillis < notBeforeMillis || atMillis > notAfterMillis) return VerificationReason.OUTSIDE_WINDOW
        return null
    }

    private fun checkSignatureShape(member: MemberAttestation): String? {
        if (member.signatures.isEmpty()) return VerificationReason.BAD_SIGNATURE_SHAPE
        val seen = mutableSetOf<String>()
        for (signature in member.signatures) {
            if (signature.keyId.isBlank() || !seen.add(signature.keyId)) return VerificationReason.BAD_SIGNATURE_SHAPE
        }
        return null
    }

    /** The sequence never goes backwards, one sequence names one entry, and a new key is also signed by the previous one. */
    private fun isContinuous(homeserver: RosterHomeserver, member: MemberAttestation, prior: MemberPrior, canonical: ByteArray): Boolean {
        val keyChanged = homeserver.signingKey != prior.signingKey
        if (member.sequence < prior.sequence) return false
        if (member.sequence == prior.sequence) {
            if (keyChanged) return false
            return prior.entryHash == null || prior.entryHash == MerkleTree.sha256Hex(canonical)
        }
        if (!keyChanged) return true
        if (prior.keyId != null && prior.keyId == member.keyId) return false
        val previousKey = prior.signingKey?.let { Ed25519Signatures.rawPublicKey(it) } ?: return false
        return member.signatures
            .filter { if (prior.keyId != null) it.keyId == prior.keyId else it.keyId != member.keyId }
            .any { Ed25519Signatures.verify(previousKey, canonical, it.signatureB64) }
    }

    companion object {
        const val DEFAULT_MAX_LIFETIME_MILLIS: Long = 400L * 24 * 60 * 60 * 1000
        private const val NANOS_PER_MILLI = 1_000_000
    }
}
